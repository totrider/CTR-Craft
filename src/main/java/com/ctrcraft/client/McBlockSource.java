package com.ctrcraft.client;

import java.util.List;

import com.ctrcraft.ModTags;
import com.ctrcraft.compat.CopycatCompat;
import com.ctrcraft.terrain.BlockSource;
import com.ctrcraft.terrain.Surface;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The terrain model's view of Minecraft: block collision boxes, and block tags for what a surface is. */
public final class McBlockSource implements BlockSource {
    private final Level level;
    private final Entity context;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public McBlockSource(Level level, Entity context) {
        this.level = level;
        this.context = context;
    }

    @Override
    public void boxes(double x0, double y0, double z0, double x1, double y1, double z1, BoxVisitor visitor) {
        // inset a hair so only the blocks inside the region count, not those touching it
        AABB region = new AABB(x0 + 1e-4, y0, z0 + 1e-4, x1 - 1e-4, y1, z1 - 1e-4);
        for (VoxelShape shape : level.getBlockCollisions(context, region)) {
            shape.forAllBoxes(visitor::box);
        }
    }

    @Override
    public int surface(int bx, int by, int bz) {
        pos.set(bx, by, bz);
        BlockState state = level.getBlockState(pos);
        int code;
        // a copycat (Create, Copycats+) is the block it copies
        List<BlockState> copied = state.hasBlockEntity() ? CopycatCompat.materials(level, pos) : List.of();
        if (copied.isEmpty()) {
            code = classify(state);
        } else {
            code = Surface.ASPHALT;
            for (BlockState material : copied) {
                int c = classify(material);
                if ((c & Surface.PAD) != 0) {
                    code |= Surface.PAD;
                }
                if ((code & 63) == Surface.ASPHALT) {
                    code = (code & Surface.PAD) | (c & 63);
                }
            }
        }
        // a boost plate lying on this block (it has no collision, so it is not the ground itself) boosts too
        if ((code & Surface.PAD) == 0 && level.getBlockState(pos.set(bx, by + 1, bz)).is(ModTags.TURBO_PAD)) {
            code |= Surface.PAD;
        }
        return code;
    }

    /** One block's surface code from the {@code ctrcraft:surface/*} and {@code ctrcraft:turbo_pad} tags. */
    private static int classify(BlockState state) {
        int code;
        if (state.is(ModTags.SURFACE_ICE)) code = Surface.ICE;
        else if (state.is(ModTags.SURFACE_MUD)) code = Surface.MUD;
        else if (state.is(ModTags.SURFACE_SNOW)) code = Surface.SNOW;
        else if (state.is(ModTags.SURFACE_ICY_ROAD)) code = Surface.ICY_ROAD;
        else if (state.is(ModTags.SURFACE_SLOW_DIRT)) code = Surface.SLOW_DIRT;
        else if (state.is(ModTags.SURFACE_DIRT)) code = Surface.DIRT;
        else if (state.is(ModTags.SURFACE_WATER)) code = Surface.WATER;
        else if (state.is(ModTags.SURFACE_SIDESLIP)) code = Surface.SIDESLIP;
        else code = Surface.ASPHALT;
        if (state.is(ModTags.TURBO_PAD)) {
            code |= Surface.PAD;
        }
        return code;
    }
}
