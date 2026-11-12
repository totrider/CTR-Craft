package com.ctrcraft.track;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A node block's item: placing one is a track edit, so it follows {@link TrackPermissions}. */
public class TrackNodeItem extends BlockItem {
    public TrackNodeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (!super.canPlace(context, state)) {
            return false;
        }
        if (context.getLevel().isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            return true;
        }
        if (!TrackPermissions.canEdit(player, context.getClickedPos())) {
            player.displayClientMessage(Component.translatable("message.ctrcraft.track.no_permission"), true);
            return false;
        }
        // replacing a checkpoint with a start node: only if its track has no other start
        if (getBlock() instanceof TrackNodeBlock node && context.getLevel() instanceof ServerLevel server) {
            TrackGraph graph = TrackData.get(server).graph();
            long pos = context.getClickedPos().asLong();
            if (graph.contains(pos) && !graph.canBecome(pos, node.kind())) {
                player.displayClientMessage(Component.translatable("message.ctrcraft.track.two_starts"), true);
                return false;
            }
        }
        return true;
    }
}
