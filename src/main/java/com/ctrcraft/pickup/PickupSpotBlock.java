package com.ctrcraft.pickup;

import java.util.ArrayList;
import java.util.List;

import com.ctrcraft.items.HeldItem;
import com.ctrcraft.kart.WrenchItem;
import com.ctrcraft.track.TrackPermissions;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A spot where a fruit, a fruit crate or a power-up crate appears while a race is on near it (or while it has a redstone signal). A small marker
 * block, no collision. A power-up crate spot is set to a particular power-up by right-clicking it with the Wrench (it cycles through them; the
 * first stop is CTR's own roulette).
 */
public class PickupSpotBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 8.0, 12.0);

    private final PickupKind kind;

    public PickupSpotBlock(Properties properties, PickupKind kind) {
        super(properties);
        this.kind = kind;
    }

    public PickupKind kind() {
        return kind;
    }

    /** Drawn by {@link com.ctrcraft.client.PickupSpotRenderer}, and only while an editing item is in hand: the marker is a tool for the builder, not part of the scenery. */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PickupSpotBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != com.ctrcraft.ModBlockEntities.PICKUP_SPOT.get()) return null;
        return (lvl, pos, st, be) -> PickupSpotBlockEntity.serverTick(lvl, pos, st, (PickupSpotBlockEntity) be);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(stack.getItem() instanceof WrenchItem) || kind != PickupKind.POWERUP_CRATE) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !TrackPermissions.canEdit(sp, pos)
                || !(level.getBlockEntity(pos) instanceof PickupSpotBlockEntity be)) {
            return ItemInteractionResult.CONSUME;
        }
        List<String> ids = new ArrayList<>();
        ids.add("");
        for (HeldItem i : HeldItem.values()) ids.add(i.id());
        int next = (ids.indexOf(be.itemId()) + 1) % ids.size();
        be.setItemId(ids.get(next));
        String id = ids.get(next);
        sp.displayClientMessage(id.isEmpty() ? Component.translatable("message.ctrcraft.spot.roulette")
                : Component.translatable("message.ctrcraft.spot.fixed", ItemManager.nameOf(id)), true);
        return ItemInteractionResult.CONSUME;
    }
}
