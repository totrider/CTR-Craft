package com.ctrcraft.items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * What a power-up crate can give: either CTR's own rule ({@link #ctr()}: the item set follows the driver's place, {@link ItemTables}) or a flat
 * weighted list of item ids that a rule set swaps in. Ids are namespaced strings, {@code ctrcraft:potion} for the built-in ones, so items from
 * data packs fit the same list. No Minecraft imports.
 */
public final class PowerupTable {
    private static final PowerupTable CTR = new PowerupTable(null);

    private final Map<String, Integer> weights;

    private PowerupTable(Map<String, Integer> weights) {
        this.weights = weights;
    }

    /** The original game's roulette: by place, from the sets in {@link ItemTables}. */
    public static PowerupTable ctr() {
        return CTR;
    }

    /** A flat table: every id with its weight (an item with weight 2 comes up twice as often as one with weight 1). Zero weights are dropped. */
    public static PowerupTable custom(Map<String, Integer> weights) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        weights.forEach((id, w) -> {
            if (w > 0) copy.put(id, w);
        });
        if (copy.isEmpty()) throw new IllegalArgumentException("a power-up table needs at least one item with a weight above 0");
        return new PowerupTable(copy);
    }

    public boolean isCtr() {
        return weights == null;
    }

    /** The weights of a custom table (empty for CTR's). */
    public Map<String, Integer> weights() {
        return weights == null ? Map.of() : Map.copyOf(weights);
    }

    /** The item id drawn for a kart in place {@code rank} (0 first) of {@code field}, on lap {@code lapIndex}. */
    public String draw(int field, int rank, int lapIndex, Random random) {
        if (weights == null) return ItemTables.draw(field, rank, lapIndex, random).id();
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        throw new IllegalStateException("unreachable");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PowerupTable t && java.util.Objects.equals(weights, t.weights);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(weights);
    }
}
