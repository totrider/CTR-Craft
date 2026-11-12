package com.ctrcraft.kart;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Boost stairs. The arrow's direction is relative to the stair (see {@link PadDirection}): UP (the default) leads up the stairs,
 * a click with an empty hand turns it clockwise: up, right, down, left. Turning or mirroring the whole stair keeps it relative.
 */
public class BoostStairsBlock extends StairBlock {
    public static final EnumProperty<PadDirection> DIRECTION = EnumProperty.create("direction", PadDirection.class);
    public static final MapCodec<BoostStairsBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockState.CODEC.fieldOf("base_state").forGetter(b -> b.padBase), propertiesCodec())
            .apply(instance, BoostStairsBlock::new));

    private final BlockState padBase;

    public BoostStairsBlock(BlockState base, Properties properties) {
        super(base, properties);
        this.padBase = base;
        registerDefaultState(defaultBlockState().setValue(DIRECTION, PadDirection.UP));
    }

    @Override
    public MapCodec<? extends StairBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DIRECTION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(DIRECTION, PadDirection.UP);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return BoostPads.turn(level, pos, player, state.setValue(DIRECTION, state.getValue(DIRECTION).next()));
    }
}
