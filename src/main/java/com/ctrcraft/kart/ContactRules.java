package com.ctrcraft.kart;

import com.ctrcraft.physics.KartSim;

/**
 * How a kart and any other Minecraft entity (mob, boat, minecart, player on foot) meet. This is the mod's own system, not CTR's: the
 * decomp only has kart against kart (see {@code KartBump}). The kart's side is the same CTR bounce with the entity as a body of a given
 * weight and reach; the entity's side (knockback, damage) is worked out here from the closing speed. All numbers are tuning, not data.
 * No Minecraft imports.
 */
public final class ContactRules {
    private ContactRules() {}

    /** The speed (blocks/s) below which a kart's touch does no damage: bumping around at a walk is harmless. */
    /** The scale the damage numbers were tuned at (a Balanced kart at top speed is 31 of their blocks a second); the kart physics' own scale is KartScale.LU_PER_BLOCK. */
    public static final double DAMAGE_LU_PER_BLOCK = 50.0;
    public static final double HARMLESS_BELOW = 6.0;
    /** Damage in half-hearts per block/s over {@link #HARMLESS_BELOW}: at the Balanced top speed (about 31 blocks/s) 12 hp, a zombie in two hits. */
    public static final double DAMAGE_PER_BPS = 0.5;
    /** The most an entity is thrown, blocks per tick. */
    public static final double MAX_KNOCKBACK = 3.0;
    /** Game ticks between two hits of the same kart on the same entity. */
    public static final int HIT_COOLDOWN_TICKS = 10;
    /** How far the kart may be from what it reports hitting, blocks (the server's sanity check). */
    public static final double MAX_REACH_BLOCKS = 6.0;

    /** An entity's weight against the kart's 256: its bounding box volume (blocks) times 200, so a cow is about a kart and a golem five. */
    public static int weight(double width, double height) {
        return (int) Math.max(64, Math.min(4096, Math.round(200.0 * width * width * height)));
    }

    /** The centre distance (lu) at which a kart touches an entity of this width: the kart's own sphere plus the entity's half width. */
    public static double reach(double width) {
        return KartSim.SPHERE_RADIUS + width / 2.0 * KartScale.LU_PER_BLOCK;
    }

    /** A bump's strength (velocity units: lu/256 per frame) as a speed in blocks per second (30 frames a second). The damage numbers were tuned with a kart's top speed at 31 of these; they keep their meaning in terms of the kart's speed, not the world's blocks, so this stays at the old 50 lu a block ({@link #DAMAGE_LU_PER_BLOCK}) when the kart physics' scale changed. */
    public static double blocksPerSecond(int impact) {
        return impact / 256.0 / DAMAGE_LU_PER_BLOCK * 30.0;
    }

    /** Damage in half-hearts for a hit at {@code bps} blocks/s; {@code scale} is the server's multiplier (0 turns damage off). */
    public static float damage(double bps, double scale) {
        return (float) Math.max(0.0, (bps - HARMLESS_BELOW) * DAMAGE_PER_BPS * scale);
    }

    /** How fast the entity is thrown (blocks per tick): the closing speed, shared by weight, so a heavy thing hardly moves. */
    public static double knockback(double bps, int kartWeight, int entityWeight) {
        double k = bps / 20.0 * kartWeight / (double) (kartWeight + entityWeight);
        return Math.min(MAX_KNOCKBACK, k);
    }
}
