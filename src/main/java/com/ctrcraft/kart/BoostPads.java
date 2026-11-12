package com.ctrcraft.kart;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** What the four boost blocks (plate, block, slab, stairs) share. */
final class BoostPads {
    private BoostPads() {}

    /** A click with an empty hand turns the arrow: swap in {@code next} with a soft click. */
    static InteractionResult turn(Level level, BlockPos pos, Player player, BlockState next) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        level.setBlock(pos, next, 3);
        level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.4F, 0.7F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
