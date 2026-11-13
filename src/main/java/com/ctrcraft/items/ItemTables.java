package com.ctrcraft.items;

import java.util.Random;

/**
 * CTR's weapon crate roulette: which item set a driver draws from by their place, and the sets themselves. A port of
 * {@code VehPhysGeneral_SetHeldItem} (ctr-native game/Vehicle/VehPhysGeneral.c:1681-1905) with the tables of {@code zGlobal_DATA.c:8154-8216}
 * (each entry is a HELD_ITEM_* id; an item's chance is its share of the entries).
 *
 * <p>CTR picks the set by how many karts are in the race (players plus bots: 8 in arcade) and the driver's rank. This port keeps its cases for
 * 2, 3, 4, 5, 6 and 8 karts exactly. CTR has nothing for other field sizes (it falls back to a flat random item): for those the mod maps the
 * rank onto the 8-kart case ({@link #setFor}), which is an extension, and a lone kart draws from the first-place set. No Minecraft imports.
 */
public final class ItemTables {
    private ItemTables() {}

    /** ITEMSET_RNG_BUCKET_COUNT: the roll is 0..199. */
    public static final int RNG_BUCKETS = 0xC8;

    /** The sets, in CTR's order (the enum ItemSet; the rank-based ones first). */
    public enum Set {
        RACE1(new int[] {4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 1, 1, 0, 6}, 0x14),
        RACE2(new int[] {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 10, 10, 10,
                0, 0, 0, 0, 0, 6, 6, 6, 2, 2, 2, 11, 11, 9, 9, 9, 9, 9, 7, 7, 7, 7, 7, 7, 7, 8}, 0x34),
        RACE3(new int[] {4, 3, 1, 10, 10, 0, 0, 6, 2, 2, 11, 9, 9, 9, 7, 7, 7, 7, 7, 8}, 0x14),
        /** CTR stores 20 entries but samples only the first 0x13 (the final turbo is never drawn). */
        RACE4(new int[] {0, 6, 2, 11, 9, 9, 9, 9, 9, 7, 7, 7, 7, 7, 7, 7, 7, 8, 8, 0}, 0x13),
        BOSS_RACE(new int[] {4, 3, 1, 10, 10, 0, 0, 6, 2, 11, 9, 9, 9, 7, 7, 7, 7, 7, 8, 8}, 0x14),
        BATTLE_DEFAULT(new int[] {4, 3, 1, 1, 1, 10, 10, 6, 6, 2, 2, 2, 2, 2, 7, 7, 7, 13, 13, 12}, 0x14);

        private final int[] entries;
        private final int sampled;

        Set(int[] entries, int sampled) {
            this.entries = entries;
            this.sampled = sampled;
        }

        /** The entries CTR can draw from, as item ids. */
        public int[] entries() {
            return java.util.Arrays.copyOf(entries, sampled);
        }

        /** The item for a roll of 0..199: {@code table[(roll * count) / 200]}. */
        public HeldItem pick(int roll) {
            return HeldItem.byCtrId(entries[(roll * sampled) / RNG_BUCKETS]);
        }

        public HeldItem pick(Random random) {
            return pick(random.nextInt(RNG_BUCKETS));
        }
    }

    /**
     * The set a kart in place {@code rank} (0 is first) of {@code field} karts draws from, on lap {@code lapIndex} (0 is the first).
     * {@code coin} is CTR's one random bit, used for second place in a three-kart race.
     */
    public static Set setFor(int field, int rank, int lapIndex, boolean coin) {
        Set set;
        if (field == 2) {
            set = rank == 0 ? Set.RACE1 : Set.BOSS_RACE;
        } else if (field == 3) {
            if (rank == 0) set = Set.RACE1;
            else if (rank == 1) set = coin ? Set.RACE2 : Set.RACE3;
            else set = Set.RACE4;
        } else if (field == 4) {
            set = byIndex(rank);
        } else if (field == 5) {
            set = rank >= 4 ? Set.RACE4 : byIndex(rank);
        } else if (field == 6) {
            if (rank == 0) set = Set.RACE1;
            else if (rank == 5) set = Set.RACE4;
            else set = byIndex((rank - 1) / 2 + 1);
        } else if (field == 8) {
            set = rank == 1 ? Set.RACE2 : byIndex(rank / 2);
        } else if (field <= 1) {
            set = Set.RACE1;
        } else {
            // 7 karts, or more than 8: CTR has no case. The mod uses the 8-kart one with the rank scaled onto it.
            set = setFor(8, Math.min(7, rank * 8 / field), lapIndex, coin);
        }
        // CTR: the last-place set is not given on the first lap
        if (set == Set.RACE4 && lapIndex == 0) set = Set.RACE3;
        return set;
    }

    private static Set byIndex(int i) {
        return switch (Math.max(0, Math.min(3, i))) {
            case 0 -> Set.RACE1;
            case 1 -> Set.RACE2;
            case 2 -> Set.RACE3;
            default -> Set.RACE4;
        };
    }

    /** The whole draw: the set for this place, then the item from it. */
    public static HeldItem draw(int field, int rank, int lapIndex, Random random) {
        return setFor(field, rank, lapIndex, random.nextBoolean()).pick(random);
    }
}
