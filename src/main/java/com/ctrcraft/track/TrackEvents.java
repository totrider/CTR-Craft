package com.ctrcraft.track;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModBlocks;
import com.ctrcraft.ModItems;

import net.minecraft.network.chat.Component;
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
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0 || !(holdsTrackItem(player) || TestDriveManager.isActive(player))) {
            return;
        }
        TrackGraph graph = TrackData.get(player.serverLevel()).graph();
        long key = ((long) player.level().dimension().location().hashCode() << 32) ^ (graph.version() & 0xFFFFFFFFL);
        Long last = LAST_SENT.get(player.getUUID());
        if (last == null || last != key) {
            LAST_SENT.put(player.getUUID(), key);
            PacketDistributor.sendToPlayer(player, TrackSnapshotPayload.of(graph));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
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
