package com.ctrcraft.kart;

import net.minecraft.util.StringRepresentable;

/**
 * Which way a boost stair's arrow points, relative to someone climbing it: the stair's front (low side) is behind them. UP is
 * towards the high side, DOWN towards the low side, LEFT and RIGHT across it as the climber sees them.
 */
public enum PadDirection implements StringRepresentable {
    UP("up"),
    RIGHT("right"),
    DOWN("down"),
    LEFT("left");

    private final String name;

    PadDirection(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The next one clockwise (what a click with an empty hand does). */
    public PadDirection next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
