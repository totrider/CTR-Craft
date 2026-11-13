package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.ctrcraft.CtrConfig;
import com.ctrcraft.ModSounds;
import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The race runtime (server). A race belongs to the Start node of a recorded, valid track and runs through four phases:
 * GATHER (a countdown while karts drive into the area round the start node), LIGHTS (the entrants are put on the grid in a random
 * order and held, revving; the three lights; the decomp's 0xF00 = 3840 ms), RACING (laps counted by passing the rings of the recorded
 * main path, see {@link RaceProgress}) and DONE. The drivers' clients run the physics; this follows their reported positions.
 * See docs/DESIGN_TRACKS.md. Everything here is a default behaviour: nothing changes how a kart drives.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID)
public final class RaceManager {
    private RaceManager() {}

    /** Gathering takes this long (ticks) after the race is started from the menu. */
    public static final int GATHER_TICKS = 200;
    /** A racer may respawn once in this many ticks. */
    private static final int RESPAWN_COOLDOWN_TICKS = 60;
    /** Once somebody has finished, the others have this long (ticks) to follow. */
    private static final int FINISH_GRACE_TICKS = 1200;
    private static final int TICK_MS = 50;

    private record Key(ResourceKey<Level> dimension, long start) {}

    private static final class Racer {
        final UUID id;
        final Component name;
        RaceProgress progress;
        double x;
        double z;
        int lapStartTick;
        int lastLapMs;
        int bestLapMs;
        /** The time of each lap finished so far, ms. */
        final List<Integer> lapTimes = new ArrayList<>();
        int finishPlace;
        int finishMs;
        boolean out;
        int lastRespawnTick = -1000;
        int placeSeq;
        RaceGrid.Slot slot;

        Racer(UUID id, Component name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class Race {
        final Key key;
        final int laps;
        final UUID starter;
        final int graphVersion;
        /** Started from the menu (entrants are whoever is in the area) rather than by karts driving in. */
        final boolean manual;
        int phase = RacePayload.GATHER;
        int ticks = GATHER_TICKS;
        int lastLights;
        int goTick;
        int firstFinishTick = -1;
        int finished;
        final Map<UUID, Racer> racers = new LinkedHashMap<>();

        Race(Key key, int laps, UUID starter, int graphVersion, boolean manual) {
            this.manual = manual;
            this.key = key;
            this.laps = laps;
            this.starter = starter;
            this.graphVersion = graphVersion;
        }
    }

    /** A racer's place for the power-up roulette: the field size, the place (0 is first) and the lap (0 is the first). */
    public record Standing(int field, int rank, int lapIndex) {}

    public static Standing standing(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING) return null;
        Racer rc = r.racers.get(player.getUUID());
        if (rc == null) return null;
        return new Standing(r.racers.size(), Math.max(0, ranking(r).indexOf(rc)), rc.progress.laps());
    }

    /**
     * Whether a race that has reached the lights is on a track whose line (the links between its nodes) passes within {@code radius} blocks of
     * {@code pos}: the spots near a track belong to it (docs/DESIGN_ITEMS.md).
     */
    public static boolean activeNear(ServerLevel level, BlockPos pos, double radius) {
        double sq = radius * radius;
        for (Race r : RACES.values()) {
            if (r.phase != RacePayload.LIGHTS && r.phase != RacePayload.RACING) continue;
            if (!r.key.dimension().equals(level.dimension())) continue;
            if (TrackData.get(level).graph().distSqToTrack(r.key.start(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= sq) return true;
        }
        return false;
    }

    /** Whether a race on the track of this Start node has started (the lights are out): until then the grid and the gathering area stay shown. */
    public static boolean raceRunning(ServerLevel level, long start) {
        Race r = RACES.get(new Key(level.dimension(), start));
        return r != null && r.phase == RacePayload.RACING;
    }

    private static final Map<Key, Race> RACES = new HashMap<>();
    private static final Map<UUID, Race> BY_PLAYER = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static int placeCounter;

    public static boolean isRunning(ServerLevel level, long start) {
        return RACES.containsKey(new Key(level.dimension(), start));
    }

    /** Starts the gathering for the race on the track of the Start node at {@code startPos}. Returns an error message key, or null if it started. */
    public static String start(ServerPlayer starter, ServerLevel level, BlockPos startPos) {
        TrackData data = TrackData.get(level);
        long start = startPos.asLong();
        TrackRoutes routes = data.routes(start);
        if (!routes.valid(data.graph(), start)) return "message.ctrcraft.race.not_valid";
        Key key = new Key(level.dimension(), start);
        if (RACES.containsKey(key)) return "message.ctrcraft.race.running";
        RACES.put(key, new Race(key, routes.laps(), starter.getUUID(), data.graph().version(), true));
        broadcastGather(level, RACES.get(key), startPos);
        return null;
    }

    public static void cancel(ServerLevel level, long start) {
        Race r = RACES.get(new Key(level.dimension(), start));
        if (r != null) end(level.getServer(), r, "message.ctrcraft.race.cancelled", false);
    }

    /**
     * Whether the player is in a race that has reached the lights and has not finished it: such a driver cannot get out of the kart by accident (sneak does nothing);
     * the "leave race" key ({@link LeaveRacePayload}) is the way out.
     */
    public static boolean locked(UUID id) {
        Race r = BY_PLAYER.get(id);
        if (r == null || (r.phase != RacePayload.LIGHTS && r.phase != RacePayload.RACING)) return false;
        Racer rc = r.racers.get(id);
        return rc != null && !rc.out && rc.finishPlace == 0;
    }

    /** The driver chose to leave the race: out of it (the kart is theirs again). */
    public static void forfeit(ServerPlayer p) {
        if (!locked(p.getUUID())) return;
        leave(p.getUUID());
        PacketDistributor.sendToPlayer(p, RacePayload.off());
        p.displayClientMessage(Component.translatable("message.ctrcraft.race.left"), true);
    }

    /** In a race a dismount (sneaking) is refused: leaving is a deliberate act, the held leave-race key. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onDismount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof KartEntity && event.getEntityMounting() instanceof ServerPlayer p
                && !p.isRemoved() && p.isAlive() && locked(p.getUUID())) {
            event.setCanceled(true);
            if (p.tickCount % 40 == 0) p.displayClientMessage(Component.translatable("message.ctrcraft.race.leave_hint"), true);
        }
    }

    /** A racer logged out, left the dimension or the kart: out of the race. */
    public static void leave(UUID id) {
        Race r = BY_PLAYER.remove(id);
        if (r != null) {
            Racer rc = r.racers.get(id);
            if (rc != null) rc.out = true;
        }
    }

    private static boolean inArea(ServerPlayer p, BlockPos start, boolean manual) {
        if (!(p.getVehicle() instanceof KartEntity kart) || kart.getControllingPassenger() != p || (!manual && !kart.canStartRaces())) return false;
        TrackGraph.Node node = TrackData.get(p.serverLevel()).graph().get(start.asLong());
        return node != null && GatherZone.contains(start.asLong(), node.rotation, p.getX(), p.getY(), p.getZ());
    }

    private static List<ServerPlayer> inArea(ServerLevel level, BlockPos start, boolean manual) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (inArea(p, start, manual)) out.add(p);
        return out;
    }

    private static void broadcastGather(ServerLevel level, Race r, BlockPos start) {
        List<ServerPlayer> in = inArea(level, start, r.manual);
        RacePayload payload = new RacePayload(RacePayload.GATHER, r.ticks, 0, r.laps, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, in.size(), List.of(), List.of());
        for (ServerPlayer p : level.players()) {
            // everybody near sees the countdown, so drivers can find the area; those in it are counted
            if (inArea(p, start, r.manual) || p.distanceToSqr(start.getX(), start.getY(), start.getZ()) < 64 * 64) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RACES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Race r : new ArrayList<>(RACES.values())) {
            ServerLevel level = server.getLevel(r.key.dimension());
            if (level == null) {
                end(server, r, null, false);
                continue;
            }
            TrackGraph graph = TrackData.get(level).graph();
            BlockPos start = BlockPos.of(r.key.start());
            if (graph.version() != r.graphVersion && (!graph.contains(r.key.start()) || !TrackData.get(level).routes(r.key.start()).valid(graph, r.key.start()))) {
                end(server, r, "message.ctrcraft.race.track_changed", false);
                continue;
            }
            switch (r.phase) {
                case RacePayload.GATHER -> tickGather(level, r, start);
                case RacePayload.LIGHTS -> tickLights(level, r);
                case RacePayload.RACING -> tickRacing(level, r);
                default -> { }
            }
        }
    }

    private static void tickGather(ServerLevel level, Race r, BlockPos start) {
        r.ticks--;
        if (r.ticks % 20 == 0 && r.ticks > 0) {
            broadcastGather(level, r, start);
            if (r.ticks <= 100) level.playSound(null, start, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (r.ticks > 0) return;
        List<ServerPlayer> entrants = inArea(level, start, r.manual);
        if (entrants.isEmpty() || (!r.manual && entrants.size() < CtrConfig.RACE_MIN_RACERS.get())) {
            end(level.getServer(), r, "message.ctrcraft.race.nobody", false);
            return;
        }
        TrackData data = TrackData.get(level);
        TrackRoutes routes = data.routes(r.key.start());
        TrackGraph.Node startNode = data.graph().get(r.key.start());
        List<ServerPlayer> order = RaceGrid.shuffled(entrants, RANDOM);
        for (int i = 0; i < order.size(); i++) {
            ServerPlayer p = order.get(i);
            Racer rc = new Racer(p.getUUID(), p.getName());
            rc.progress = new RaceProgress(data.graph(), routes);
            rc.slot = RaceGrid.slot(r.key.start(), startNode.rotation, i);
            rc.placeSeq = ++placeCounter;
            r.racers.put(p.getUUID(), rc);
            BY_PLAYER.put(p.getUUID(), r);
            com.ctrcraft.pickup.ItemManager.reset(p);          // a new race starts with nothing in the kart
            put(level, p, rc);
        }
        r.phase = RacePayload.LIGHTS;
        r.ticks = RaceGrid.INTRO_TICKS + RaceGrid.LIGHTS_TICKS + 20;       // the intro (jingle and camera pan), then a second to settle on the grid before the first light
        r.lastLights = 0;
        sendAll(level, r, 0);
    }

    private static void tickLights(ServerLevel level, Race r) {
        r.ticks--;
        int lit = RaceGrid.lightsLit(r.ticks);
        BlockPos start = BlockPos.of(r.key.start());
        if (lit != r.lastLights) {
            r.lastLights = lit;
            if (lit > 0) level.playSound(null, start, ModSounds.LIGHT_COUNT.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            sendAll(level, r, 0);
        }
        if (r.ticks > 0) return;
        r.phase = RacePayload.RACING;
        r.goTick = level.getServer().getTickCount();
        for (Racer rc : r.racers.values()) rc.lapStartTick = r.goTick;
        level.playSound(null, start, ModSounds.LIGHT_GREEN.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
        sendAll(level, r, 0);
    }

    private static void tickRacing(ServerLevel level, Race r) {
        int now = level.getServer().getTickCount();
        boolean anyActive = false;
        for (Racer rc : r.racers.values()) {
            if (rc.out || rc.finishPlace != 0) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(rc.id);
            if (p == null || !(p.getVehicle() instanceof KartEntity) || !p.level().dimension().equals(r.key.dimension())) {
                rc.out = true;
                BY_PLAYER.remove(rc.id);
                if (p != null) PacketDistributor.sendToPlayer(p, RacePayload.off());
                continue;
            }
            anyActive = true;
        }
        boolean timeUp = r.firstFinishTick >= 0 && now - r.firstFinishTick > FINISH_GRACE_TICKS;
        if (!anyActive || timeUp) {
            end(level.getServer(), r, null, true);
            return;
        }
        if (now % 4 == 0) sendAll(level, r, now);
    }

    /** Moves the racer's kart and rider to the slot the racer holds (grid or respawn). The driver's client then places its sim there. */
    private static void put(ServerLevel level, ServerPlayer p, Racer rc) {
        if (p.getVehicle() instanceof KartEntity kart) {
            kart.absMoveTo(rc.slot.x(), rc.slot.y(), rc.slot.z(), rc.slot.yaw(), 0.0F);
            kart.setDeltaMovement(Vec3.ZERO);
            kart.positionRider(p);
            level.getChunkSource().move(p);
        }
        rc.x = rc.slot.x();
        rc.z = rc.slot.z();
    }

    /** Respawn: back to the last checkpoint you passed, facing the next one. */
    public static void respawn(ServerPlayer player) {
        Race r = BY_PLAYER.get(player.getUUID());
        Racer rc = r == null ? null : r.racers.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING || rc == null || rc.out || rc.finishPlace != 0) {
            player.displayClientMessage(Component.translatable("message.ctrcraft.race.no_respawn"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        int now = level.getServer().getTickCount();
        if (now - rc.lastRespawnTick < RESPAWN_COOLDOWN_TICKS) return;
        rc.lastRespawnTick = now;
        long node = rc.progress.lastPassed();
        long next = rc.progress.expected().iterator().next();
        double dx = NodeGeometry.x(next) - NodeGeometry.x(node);
        double dz = NodeGeometry.z(next) - NodeGeometry.z(node);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        rc.slot = new RaceGrid.Slot(NodeGeometry.x(node) + 0.5, NodeGeometry.y(node), NodeGeometry.z(node) + 0.5, yaw);
        rc.placeSeq = ++placeCounter;
        put(level, player, rc);
        sendOne(level, r, rc, now);
        player.displayClientMessage(Component.translatable("message.ctrcraft.race.respawned"), true);
    }

    // ---- starting by driving in: enter and exit tracking ----

    /** Who is inside each Start node's gathering box (a kart that may start races): entering is what can begin a race, so the sets are kept apart from any race. */
    private static final Map<Key, Set<UUID>> INSIDE = new HashMap<>();

    /** Updates the areas a kart's new position is in or out of. Runs on every report, but only compares against the Start nodes. */
    private static void trackAreas(ServerPlayer player, double x, double y, double z) {
        ServerLevel level = player.serverLevel();
        boolean can = player.getVehicle() instanceof KartEntity k && k.canStartRaces();
        for (long start : TrackData.get(level).starts()) {
            TrackGraph.Node node = TrackData.get(level).graph().get(start);
            boolean in = can && node != null && GatherZone.contains(start, node.rotation, x, y, z);
            Key key = new Key(level.dimension(), start);
            Set<UUID> set = INSIDE.computeIfAbsent(key, k -> new HashSet<>());
            if (in) {
                if (set.add(player.getUUID())) entered(level, key, set.size());
            } else if (set.remove(player.getUUID())) {
                exited(level.getServer(), key, set.size());
            }
        }
    }

    private static void entered(ServerLevel level, Key key, int count) {
        if (RACES.containsKey(key) || count < CtrConfig.RACE_MIN_RACERS.get()) return;
        TrackData data = TrackData.get(level);
        TrackRoutes routes = data.routes(key.start());
        if (!routes.valid(data.graph(), key.start())) return;
        Race race = new Race(key, routes.laps(), null, data.graph().version(), false);
        RACES.put(key, race);
        broadcastGather(level, race, BlockPos.of(key.start()));
    }

    private static void exited(MinecraftServer server, Key key, int count) {
        Race r = RACES.get(key);
        if (r != null && r.phase == RacePayload.GATHER && !r.manual && count < CtrConfig.RACE_MIN_RACERS.get()) {
            end(server, r, "message.ctrcraft.race.not_enough", false);
        }
    }

    /** A player left the game, the dimension or their kart: out of every area. */
    public static void exitAll(MinecraftServer server, UUID id) {
        for (Map.Entry<Key, Set<UUID>> e : new ArrayList<>(INSIDE.entrySet())) {
            if (e.getValue().remove(id)) exited(server, e.getKey(), e.getValue().size());
        }
    }

    /** Called for each position a driver's kart reports. */
    public static void onKartMoved(ServerPlayer player, double x0, double y0, double z0, double x1, double y1, double z1) {
        trackAreas(player, x1, y1, z1);
        Race r = BY_PLAYER.get(player.getUUID());
        if (r == null || r.phase != RacePayload.RACING) return;
        Racer rc = r.racers.get(player.getUUID());
        if (rc == null || rc.out || rc.finishPlace != 0) return;
        rc.x = x1;
        rc.z = z1;
        RaceProgress.Result res = rc.progress.move(x0, y0, z0, x1, y1, z1);
        if (res.kind() != RaceProgress.Kind.LAP) return;
        ServerLevel level = player.serverLevel();
        int now = level.getServer().getTickCount();
        int lapMs = (now - rc.lapStartTick) * TICK_MS;
        rc.lapStartTick = now;
        rc.lastLapMs = lapMs;
        rc.lapTimes.add(lapMs);
        rc.bestLapMs = rc.bestLapMs == 0 ? lapMs : Math.min(rc.bestLapMs, lapMs);
        if (rc.progress.laps() >= r.laps) {
            rc.finishPlace = ++r.finished;
            rc.finishMs = (now - r.goTick) * TICK_MS;
            if (r.firstFinishTick < 0) r.firstFinishTick = now;
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            for (Racer other : r.racers.values()) {
                ServerPlayer op = level.getServer().getPlayerList().getPlayer(other.id);
                if (op != null) op.sendSystemMessage(Component.translatable("message.ctrcraft.race.finished", rc.name, rc.finishPlace, fmt(rc.finishMs)));
            }
            sendOne(level, r, rc, now);
        } else {
            player.displayClientMessage(Component.translatable("message.ctrcraft.race.lap", rc.progress.laps() + 1, r.laps, fmt(lapMs)), true);
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    /** Racers in order: finishers by finish place, then the rest by progress round the course, ties by distance to the next ring. */
    private static List<Racer> ranking(Race r) {
        List<Racer> list = new ArrayList<>(r.racers.values());
        list.sort(Comparator
                .comparingInt((Racer a) -> a.out ? 1 : 0)
                .thenComparingInt(a -> a.finishPlace != 0 ? a.finishPlace : Integer.MAX_VALUE)
                .thenComparing(Comparator.comparingDouble((Racer a) -> -a.progress.progress()))
                .thenComparingDouble(a -> a.progress.distanceToNextSq(a.x, a.z)));
        return list;
    }

    private static void sendAll(ServerLevel level, Race r, int now) {
        List<Racer> rank = ranking(r);
        for (Racer rc : r.racers.values()) {
            sendOne(level, r, rc, now, rank);
        }
    }

    private static void sendOne(ServerLevel level, Race r, Racer rc, int now) {
        sendOne(level, r, rc, now, ranking(r));
    }

    private static void sendOne(ServerLevel level, Race r, Racer rc, int now, List<Racer> rank) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(rc.id);
        if (p == null || rc.out) return;
        int place = rank.indexOf(rc) + 1;
        int timeMs = r.phase == RacePayload.RACING ? (now - r.goTick) * TICK_MS : 0;
        if (rc.finishPlace != 0) timeMs = rc.finishMs;
        boolean hold = r.phase == RacePayload.LIGHTS;
        int phase = rc.finishPlace != 0 ? RacePayload.DONE : r.phase;
        int lap = Math.min(r.laps, rc.progress.laps() + 1);
        PacketDistributor.sendToPlayer(p, new RacePayload(phase, r.ticks, r.lastLights, r.laps, lap, place, r.racers.size(), timeMs, rc.lastLapMs,
                rc.bestLapMs, hold, rc.placeSeq, rc.slot.x(), rc.slot.y(), rc.slot.z(), rc.slot.yaw(), rc.finishPlace, 0,
                rank.stream().limit(4).map(o -> o.id).toList(), List.copyOf(rc.lapTimes)));
    }

    /** An id for the race the player is in (its track), or null. */
    public static String raceIdOf(UUID id) {
        Race r = BY_PLAYER.get(id);
        return r == null ? null : r.key.dimension().location() + "@" + r.key.start();
    }

    private static void end(MinecraftServer server, Race r, String reasonKey, boolean results) {
        RACES.remove(r.key);
        ServerLevel level = server.getLevel(r.key.dimension());
        if (level != null) com.ctrcraft.pickup.MinePool.clearRace(r.key.dimension().location() + "@" + r.key.start(), level);
        List<Racer> rank = ranking(r);
        for (Racer rc : r.racers.values()) {
            BY_PLAYER.remove(rc.id);
            ServerPlayer p = server.getPlayerList().getPlayer(rc.id);
            if (p == null) continue;
            PacketDistributor.sendToPlayer(p, RacePayload.off());
            if (reasonKey != null) p.displayClientMessage(Component.translatable(reasonKey), true);
            if (results) {
                p.sendSystemMessage(Component.translatable("message.ctrcraft.race.results"));
                for (int i = 0; i < rank.size(); i++) {
                    Racer o = rank.get(i);
                    Component line = o.finishPlace != 0 ? Component.literal(i + 1 + ". ").append(o.name).append("  " + fmt(o.finishMs))
                            : Component.literal(i + 1 + ". ").append(o.name).append("  ").append(Component.translatable("message.ctrcraft.race.dnf"));
                    p.sendSystemMessage(line);
                }
            }
        }
        if (level != null && r.racers.isEmpty()) {
            // gathering that was cancelled: tell the people who were watching the countdown it is off
            BlockPos start = BlockPos.of(r.key.start());
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(start.getX(), start.getY(), start.getZ()) < 64 * 64) {
                    PacketDistributor.sendToPlayer(p, RacePayload.off());
                    if (reasonKey != null) p.displayClientMessage(Component.translatable(reasonKey), true);
                }
            }
        }
    }

    public static String fmt(int ms) {
        return String.format("%d:%02d.%03d", ms / 60000, ms / 1000 % 60, ms % 1000);
    }

    /** Drop everything (the server stopped; the statics would outlive a singleplayer world). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (Iterator<Race> it = RACES.values().iterator(); it.hasNext(); ) {
            it.next();
            it.remove();
        }
        BY_PLAYER.clear();
        INSIDE.clear();
        com.ctrcraft.pickup.MinePool.clearAll();
    }
}
