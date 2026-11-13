package com.ctrcraft.items;

/**
 * The items a weapon crate can give, with CTR's ids (HELD_ITEM_*, ctr-native include/namespace_Vehicle.h:641-657). The {@code id} string is
 * what rule sets and (later) data packs name them by. No Minecraft imports.
 */
public enum HeldItem {
    TURBO(0, "turbo"),
    BOMB(1, "bomb"),
    MISSILE(2, "missile"),
    TNT(3, "tnt"),
    POTION(4, "potion"),
    SPRING(5, "spring"),
    SHIELD(6, "shield"),
    MASK(7, "mask"),
    CLOCK(8, "clock"),
    WARPBALL(9, "warpball"),
    BOMB_3X(10, "bomb_3x"),
    MISSILE_3X(11, "missile_3x"),
    INVISIBILITY(12, "invisibility"),
    SUPER_ENGINE(13, "super_engine");

    public final int ctrId;
    public final String path;

    HeldItem(int ctrId, String path) {
        this.ctrId = ctrId;
        this.path = path;
    }

    /** The namespaced id, {@code ctrcraft:<path>}. */
    public String id() {
        return "ctrcraft:" + path;
    }

    public static HeldItem byCtrId(int ctrId) {
        for (HeldItem i : values()) {
            if (i.ctrId == ctrId) return i;
        }
        throw new IllegalArgumentException("no CTR item with id " + ctrId);
    }
}
