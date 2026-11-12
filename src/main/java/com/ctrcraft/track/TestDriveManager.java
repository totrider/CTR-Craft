package com.ctrcraft.track;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server's test drives: one per player at most. A drive starts with the Track Tool (sneak + right-click the Start node), listens to
 * the driver's kart moves (see KartStatePayload), tells the driver what happened on the action bar, and sends the client what to draw.
 * What it records is saved in {@link TrackData}.
 */
public final class TestDriveManager {
    private TestDriveManager() {}

    private record Session(TestDrive drive, ResourceKey<Level> dimension) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean isActive(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Opens the track menu of the Start node at {@code startPos} on the player's screen. */
    public static void openMenu(ServerPlayer player, ServerLevel level, BlockPos startPos) {
        TrackData data = TrackData.get(level);
        TrackRoutes routes = data.routes(startPos.asLong());
        long start = startPos.asLong();
        PacketDistributor.sendToPlayer(player, new TrackMenuPayload(start, isActive(player), routes.hasMain(), data.graph().component(start).size(),
                routes.branches().size(), routes.uncovered(data.graph(), start).size(), routes.valid(data.graph(), start), routes.laps(),
                RaceManager.isRunning(level, start)));
    }

    /** Starts a test drive of the track whose start node is at {@code startPos}. */
    public static void start(ServerPlayer player, ServerLevel level, BlockPos startPos) {
        TrackData data = TrackData.get(level);
        TestDrive drive = new TestDrive(data.graph(), data.routes(startPos.asLong()), startPos.asLong());
        SESSIONS.put(player.getUUID(), new Session(drive, level.dimension()));
        send(player, drive);
        if (drive.phase() == TestDrive.Phase.MAIN) {
            say(player, Component.translatable("message.ctrcraft.testdrive.started_main"));
        } else {
            say(player, Component.translatable("message.ctrcraft.testdrive.started_branches", drive.uncovered()));
        }
    }

    public static void stop(UUID id) {
        SESSIONS.remove(id);
    }

    public static void stop(ServerPlayer player, boolean silent) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(player, TestDrivePayload.off());
            if (!silent) say(player, Component.translatable("message.ctrcraft.testdrive.stopped"));
        }
    }

    /** Throws away the recording of the track whose start node is at {@code startPos}; a drive in progress starts over. */
    public static void reset(ServerLevel level, BlockPos startPos) {
        TrackData data = TrackData.get(level);
        data.routes(startPos.asLong()).clear();
        data.setDirty();
        for (Map.Entry<UUID, Session> e : SESSIONS.entrySet()) {
            if (e.getValue().drive().start() == startPos.asLong() && e.getValue().dimension().equals(level.dimension())) {
                e.getValue().drive().reset();
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
                if (p != null) send(p, e.getValue().drive());
            }
        }
    }

    /** Called for each position the driver's kart reports. */
    public static void onKartMoved(ServerPlayer player, double x0, double y0, double z0, double x1, double y1, double z1) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        if (!player.level().dimension().equals(s.dimension())) {
            stop(player, true);
            return;
        }
        TestDrive drive = s.drive();
        if (!drive.refresh()) {
            stop(player, true);
            say(player, Component.translatable("message.ctrcraft.testdrive.start_gone"));
            return;
        }
        TestDrive.Result r = drive.move(x0, y0, z0, x1, y1, z1);
        if (r.kind() == TestDrive.Kind.NONE) return;
        ServerLevel level = player.serverLevel();
        BlockPos at = BlockPos.of(r.node());
        level.playSound(null, at, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, r.kind() == TestDrive.Kind.NODE ? 1.2F : 1.6F);
        switch (r.kind()) {
            case NODE -> say(player, Component.translatable(drive.inBranch() ? "message.ctrcraft.testdrive.branch_node" : "message.ctrcraft.testdrive.node",
                    drive.phase() == TestDrive.Phase.MAIN ? drive.lapNodes() : drive.trail().size()));
            case LAP -> {
                TrackGraph graph = TrackData.get(level).graph();
                TrackData.get(level).setDirty();
                Component msg = Component.translatable("message.ctrcraft.testdrive.lap", drive.routes().main().size() - 1);
                int bad = drive.routes().facingAgainstCourse(graph);
                if (bad > 0) msg = msg.copy().append(" ").append(Component.translatable("message.ctrcraft.testdrive.facing", bad));
                msg = msg.copy().append(" ").append(remaining(drive));
                say(player, msg);
            }
            case BRANCH_START -> say(player, Component.translatable("message.ctrcraft.testdrive.branch_start"));
            case BRANCH_END -> {
                TrackData.get(level).setDirty();
                say(player, Component.translatable("message.ctrcraft.testdrive.branch_end", drive.routes().branches().size()).append(" ").append(remaining(drive)));
            }
            default -> { }
        }
        send(player, drive);
    }

    private static Component remaining(TestDrive drive) {
        return drive.valid() ? Component.translatable("message.ctrcraft.testdrive.valid")
                : Component.translatable("message.ctrcraft.testdrive.undriven", drive.uncovered());
    }

    private static void say(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }

    private static void send(ServerPlayer player, TestDrive drive) {
        PacketDistributor.sendToPlayer(player, TestDrivePayload.of(drive));
    }
}
