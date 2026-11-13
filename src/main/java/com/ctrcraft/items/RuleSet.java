package com.ctrcraft.items;

/**
 * The rules of a race, set on a race track's start (applied with the ruleset item, see docs/DESIGN_ITEMS.md). The default, {@link #CTR},
 * is the original game's experience. No Minecraft imports.
 *
 * @param powerups        what power-up crates give
 * @param fruit           loose wumpa fruit are spawned
 * @param fruitCrates     fruit crates are spawned
 * @param powerupCrates   power-up (weapon) crates are spawned
 * @param boostPads       boost blocks act on karts
 * @param maxSpeedPercent a cap on the top speed as a percentage of the kart class's, 0 for none
 */
public record RuleSet(PowerupTable powerups, boolean fruit, boolean fruitCrates, boolean powerupCrates, boolean boostPads, int maxSpeedPercent) {
    /** The original game's rules. */
    public static final RuleSet CTR = new RuleSet(PowerupTable.ctr(), true, true, true, true, 0);

    public RuleSet {
        if (powerups == null) throw new IllegalArgumentException("powerups");
        if (maxSpeedPercent < 0 || maxSpeedPercent > 100) throw new IllegalArgumentException("maxSpeedPercent is 0 (no cap) to 100");
    }

    public RuleSet withPowerups(PowerupTable t) {
        return new RuleSet(t, fruit, fruitCrates, powerupCrates, boostPads, maxSpeedPercent);
    }

    public RuleSet withFruit(boolean on) {
        return new RuleSet(powerups, on, fruitCrates, powerupCrates, boostPads, maxSpeedPercent);
    }

    public RuleSet withFruitCrates(boolean on) {
        return new RuleSet(powerups, fruit, on, powerupCrates, boostPads, maxSpeedPercent);
    }

    public RuleSet withPowerupCrates(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, on, boostPads, maxSpeedPercent);
    }

    public RuleSet withBoostPads(boolean on) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, on, maxSpeedPercent);
    }

    public RuleSet withMaxSpeedPercent(int percent) {
        return new RuleSet(powerups, fruit, fruitCrates, powerupCrates, boostPads, percent);
    }

    public boolean isCtr() {
        return equals(CTR);
    }
}
