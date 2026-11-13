package com.ctrcraft.pickup;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The three things a pickup spot can spawn. The look is a stand-in (a vanilla item spinning in place) until the mod's own models exist.
 * CTR's respawn: a crate comes back after 30 frames plus 16 frames of growing (about 2.3 s at 20 ticks a second is too long; the real
 * figures are 1 s and 0.5 s at 30 Hz, so 31 game ticks); fruit does not come back during a race (docs/DESIGN_ITEMS.md).
 */
public enum PickupKind {
    FRUIT("fruit", 0, 0.5, 0.62),
    FRUIT_CRATE("fruit_crate", 31, 1.0, 1.0),
    POWERUP_CRATE("powerup_crate", 31, 1.0, 1.0);

    public final String path;
    /** Game ticks until it is back after being taken; 0 means it stays gone until the spot is reset. */
    public final int respawnTicks;
    /** Its size in blocks: the original's proportions with a crate (108.8 level units) as exactly one block, the unit Minecraft works in. */
    public final double width;
    public final double height;

    PickupKind(String path, int respawnTicks, double width, double height) {
        this.path = path;
        this.respawnTicks = respawnTicks;
        this.width = width;
        this.height = height;
    }

    public Item standIn() {
        return switch (this) {
            case FRUIT -> Items.APPLE;
            case FRUIT_CRATE -> Items.BARREL;
            case POWERUP_CRATE -> Items.YELLOW_SHULKER_BOX;
        };
    }

    public static PickupKind byOrdinal(int i) {
        PickupKind[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }
}
