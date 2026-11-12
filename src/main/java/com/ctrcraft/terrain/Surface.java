package com.ctrcraft.terrain;

import com.ctrcraft.physics.Terrain;

/**
 * Surface codes: what a block is to the kart. The low bits pick one of CTR's terrain rows (spec 8.2), {@link #PAD} marks a turbo
 * pad. The Minecraft side maps blocks to codes with block tags (data-driven: a data pack can change them).
 */
public final class Surface {
    private Surface() {}

    public static final int ASPHALT = 0;
    public static final int DIRT = 1;
    public static final int SLOW_DIRT = 2;
    public static final int WATER = 3;
    public static final int ICE = 4;
    public static final int ICY_ROAD = 5;
    public static final int SNOW = 6;
    public static final int MUD = 7;
    public static final int SIDESLIP = 8;
    public static final int PAD = 64;

    public static Terrain terrain(int code) {
        return switch (code & 63) {
            case DIRT -> Terrain.DIRT;
            case SLOW_DIRT -> Terrain.SLOW_DIRT;
            case WATER -> Terrain.WATER;
            case ICE -> Terrain.ICE;
            case ICY_ROAD -> Terrain.ICY_ROAD;
            case SNOW -> Terrain.SNOW;
            case MUD -> Terrain.MUD;
            case SIDESLIP -> Terrain.SIDESLIP;
            default -> Terrain.ASPHALT;
        };
    }

    public static boolean isPad(int code) {
        return (code & PAD) != 0;
    }
}
