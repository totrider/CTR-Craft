package com.ctrcraft.track;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModBlocks;
import com.ctrcraft.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side track events: breaking a node is a track edit (permissions), and a player holding a track item is sent the nodes of
 * their dimension (to draw the ghosts) whenever they change.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID)
public final class TrackEvents {
    private TrackEvents() {}

    /** What each player was last sent: dimension and graph version, so an unchanged graph is not sent again. */
    private static final Map<UUID, Long> LAST_SENT = new HashMap<>();
    /** How many grid starts the last snapshot to each player carried (so one that goes to none is sent once more). */
    private static final Map<UUID, Integer> LAST_GRID = new HashMap<>();
    /** A complete, idle track's start grid is sent to players within this many blocks of its Start node. */
    private static final double GRID_SHOW_DISTANCE = 96.0;

    public static boolean holdsTrackItem(Player player) {
        return isTrackItem(player.getMainHandItem()) || isTrackItem(player.getOffhandItem());
    }

    public static boolean isTrackItem(ItemStack stack) {
        return stack.is(ModItems.TRACK_TOOL.get()) || stack.is(ModBlocks.START_NODE_ITEM.get()) || stack.is(ModBlocks.CHECKPOINT_NODE_ITEM.get());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof TrackNodeBlock && event.getPlayer() instanceof ServerPlayer player
                && !TrackPermissions.canEdit(player, event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.ctrcraft.track.no_permission"), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        TrackData data = TrackData.get(level);
        TrackGraph graph = data.graph();
        // the Start nodes whose grid shows with nothing in hand: a complete track whose race has not started (or none is on), near this player
        long[] grid = data.starts().stream().filter(s -> data.routes(s).valid(graph, s) && !RaceManager.raceRunning(level, s)
                && player.distanceToSqr(NodeGeometry.x(s), NodeGeometry.y(s), NodeGeometry.z(s)) < GRID_SHOW_DISTANCE * GRID_SHOW_DISTANCE)
                .mapToLong(Long::longValue).toArray();
        if (!(holdsTrackItem(player) || TestDriveManager.isActive(player) || grid.length > 0 || LAST_GRID.getOrDefault(player.getUUID(), 0) > 0)) {
            return;
        }
        long key = ((long) player.level().dimension().location().hashCode() << 32) ^ (graph.version() & 0xFFFFFFFFL) ^ (java.util.Arrays.hashCode(grid) * 31L);
        Long last = LAST_SENT.get(player.getUUID());
        if (last == null || last != key) {
            LAST_SENT.put(player.getUUID(), key);
            LAST_GRID.put(player.getUUID(), grid.length);
            PacketDistributor.sendToPlayer(player, TrackSnapshotPayload.of(graph, grid));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
        LAST_GRID.remove(event.getEntity().getUUID());
        TestDriveManager.stop(event.getEntity().getUUID());
        RaceManager.leave(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer p) RaceManager.exitAll(p.server, p.getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) TestDriveManager.stop(player, true);
        RaceManager.leave(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer p) RaceManager.exitAll(p.server, p.getUUID());
    }

    /** Stepping out of a kart is leaving every Start node's area (no more position reports would say so). */
    @SubscribeEvent
    public static void onDismount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof ServerPlayer p) {
            RaceManager.exitAll(p.server, p.getUUID());
        }
    }
}
