package com.ctrcraft.terrain;

/** Where the terrain comes from: the world's collision boxes and what its blocks are. Units are blocks, +Y up. */
public interface BlockSource {
    /** Calls {@code visitor} for every collision box (world space) of the blocks inside the region. */
    void boxes(double x0, double y0, double z0, double x1, double y1, double z1, BoxVisitor visitor);

    /** The {@link Surface} code of the block at a block position. */
    int surface(int bx, int by, int bz);

    @FunctionalInterface
    interface BoxVisitor {
        void box(double x0, double y0, double z0, double x1, double y1, double z1);
    }
}
