package com.ctrcraft.physics;

/** The terrain multipliers under the kart (MetaDataTerrain, spec section 8.2), all 256 = 1.0. */
public record Terrain(int speed, int accel, int counterSteer, int lean, int groundFriction, int turnAngle,
                      int turnResponse, int flags) {
    public static final int FLAG_ACCEL_WHILE_REVERSE_SLIDING = 0x4;
    public static final int FLAG_MUD_PHYSICS = 0x80;
    public static final int FLAG_SIDESLIP_FRICTION = 0x100;

    public static final Terrain ASPHALT = new Terrain(256, 256, 256, 256, 256, 256, 256, 0x40);
    /** In the air. */
    public static final Terrain NONE = new Terrain(256, 256, 0, 256, 256, 256, 256, 0);
    public static final Terrain DIRT = new Terrain(140, 180, 256, 256, 256, 256, 256, 0);
    public static final Terrain SLOW_DIRT = new Terrain(100, 180, 256, 256, 256, 256, 256, 0);
    public static final Terrain WATER = new Terrain(180, 200, 256, 256, 256, 256, 256, 0);
    public static final Terrain ICE = new Terrain(256, 100, 0, 0, 32, 128, 32, 0x1C);
    public static final Terrain ICY_ROAD = new Terrain(256, 256, 256, 256, 128, 256, 256, 0x40);
    public static final Terrain SNOW = new Terrain(140, 180, 256, 256, 128, 256, 256, 0);
    public static final Terrain MUD = new Terrain(140, 180, 256, 256, 512, 256, 256, FLAG_MUD_PHYSICS);
    public static final Terrain SIDESLIP = new Terrain(256, 256, 256, 256, 256, 256, 256, 0x140);
}
