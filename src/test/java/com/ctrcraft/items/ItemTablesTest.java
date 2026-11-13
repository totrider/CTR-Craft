package com.ctrcraft.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.ctrcraft.items.ItemTables.Set;

/** CTR's weapon crate roulette (VehPhysGeneral_SetHeldItem and its tables). */
class ItemTablesTest {
    private static Map<HeldItem, Integer> shares(Set set) {
        Map<HeldItem, Integer> m = new EnumMap<>(HeldItem.class);
        for (int id : set.entries()) m.merge(HeldItem.byCtrId(id), 1, Integer::sum);
        return m;
    }

    @Test
    void first_place_gets_the_defensive_set() {
        // 8/20 potion, 8/20 tnt, 2/20 bomb, 1/20 turbo, 1/20 shield
        Map<HeldItem, Integer> m = shares(Set.RACE1);
        assertEquals(20, Set.RACE1.entries().length);
        assertEquals(8, m.get(HeldItem.POTION));
        assertEquals(8, m.get(HeldItem.TNT));
        assertEquals(2, m.get(HeldItem.BOMB));
        assertEquals(1, m.get(HeldItem.TURBO));
        assertEquals(1, m.get(HeldItem.SHIELD));
    }

    @Test
    void the_second_set_has_52_entries_and_the_clock_once() {
        Map<HeldItem, Integer> m = shares(Set.RACE2);
        assertEquals(52, Set.RACE2.entries().length);
        assertEquals(10, m.get(HeldItem.POTION));
        assertEquals(5, m.get(HeldItem.WARPBALL));
        assertEquals(7, m.get(HeldItem.MASK));
        assertEquals(1, m.get(HeldItem.CLOCK));
        assertEquals(3, m.get(HeldItem.BOMB_3X));
    }

    @Test
    void the_last_place_set_never_draws_its_final_entry() {
        assertEquals(19, Set.RACE4.entries().length);
        Map<HeldItem, Integer> m = shares(Set.RACE4);
        assertEquals(1, m.get(HeldItem.TURBO)); // the 20th entry (a second turbo) is not sampled
        assertEquals(8, m.get(HeldItem.MASK));
        assertEquals(5, m.get(HeldItem.WARPBALL));
        assertEquals(2, m.get(HeldItem.CLOCK));
    }

    @Test
    void a_roll_picks_table_index_roll_times_count_over_200() {
        assertEquals(HeldItem.POTION, Set.RACE1.pick(0));
        assertEquals(HeldItem.TNT, Set.RACE1.pick(80));   // 80 * 20 / 200 = 8
        assertEquals(HeldItem.SHIELD, Set.RACE1.pick(199)); // index 19
        assertEquals(HeldItem.TURBO, Set.RACE1.pick(180)); // index 18
    }

    @Test
    void the_set_follows_the_place_in_an_eight_kart_race() {
        assertEquals(Set.RACE1, ItemTables.setFor(8, 0, 1, false));
        assertEquals(Set.RACE2, ItemTables.setFor(8, 1, 1, false));
        assertEquals(Set.RACE2, ItemTables.setFor(8, 2, 1, false));
        assertEquals(Set.RACE2, ItemTables.setFor(8, 3, 1, false));
        assertEquals(Set.RACE3, ItemTables.setFor(8, 4, 1, false));
        assertEquals(Set.RACE3, ItemTables.setFor(8, 5, 1, false));
        assertEquals(Set.RACE4, ItemTables.setFor(8, 6, 1, false));
        assertEquals(Set.RACE4, ItemTables.setFor(8, 7, 1, false));
    }

    @Test
    void the_last_place_set_is_the_third_on_the_first_lap() {
        assertEquals(Set.RACE3, ItemTables.setFor(8, 7, 0, false));
        assertEquals(Set.RACE4, ItemTables.setFor(8, 7, 1, false));
    }

    @Test
    void the_other_field_sizes_follow_their_cases() {
        // six karts
        assertEquals(Set.RACE1, ItemTables.setFor(6, 0, 1, false));
        assertEquals(Set.RACE2, ItemTables.setFor(6, 1, 1, false));
        assertEquals(Set.RACE2, ItemTables.setFor(6, 2, 1, false));
        assertEquals(Set.RACE3, ItemTables.setFor(6, 3, 1, false));
        assertEquals(Set.RACE3, ItemTables.setFor(6, 4, 1, false));
        assertEquals(Set.RACE4, ItemTables.setFor(6, 5, 1, false));
        // five and four karts: the rank is the set; fifth place of five shares the last
        assertEquals(Set.RACE4, ItemTables.setFor(5, 4, 1, false));
        assertEquals(Set.RACE3, ItemTables.setFor(4, 2, 1, false));
        // three karts: second place draws the second or third set by a coin
        assertEquals(Set.RACE2, ItemTables.setFor(3, 1, 1, true));
        assertEquals(Set.RACE3, ItemTables.setFor(3, 1, 1, false));
        assertEquals(Set.RACE4, ItemTables.setFor(3, 2, 1, false));
        // two karts: the boss-race set behind the leader
        assertEquals(Set.RACE1, ItemTables.setFor(2, 0, 1, false));
        assertEquals(Set.BOSS_RACE, ItemTables.setFor(2, 1, 1, false));
    }

    @Test
    void sizes_the_decomp_has_no_case_for_are_scaled_onto_the_eight_kart_rules() {
        assertEquals(Set.RACE1, ItemTables.setFor(1, 0, 1, false));
        assertEquals(Set.RACE1, ItemTables.setFor(12, 0, 1, false));
        assertEquals(Set.RACE4, ItemTables.setFor(12, 11, 1, false));
        assertEquals(Set.RACE4, ItemTables.setFor(7, 6, 1, false));
    }

    @Test
    void a_default_ruleset_is_the_original_game() {
        assertTrue(RuleSet.CTR.isCtr());
        assertTrue(RuleSet.CTR.powerups().isCtr());
        assertTrue(RuleSet.CTR.fruit() && RuleSet.CTR.fruitCrates() && RuleSet.CTR.powerupCrates() && RuleSet.CTR.boostPads());
        assertEquals(0, RuleSet.CTR.maxSpeedPercent());
        assertFalse(RuleSet.CTR.withFruit(false).isCtr());
    }

    @Test
    void a_custom_table_draws_by_weight_and_takes_ids_from_anywhere() {
        PowerupTable t = PowerupTable.custom(Map.of("ctrcraft:turbo", 1, "mypack:banana", 3, "ctrcraft:bomb", 0));
        Random r = new Random(7);
        int banana = 0;
        for (int i = 0; i < 4000; i++) {
            String id = t.draw(8, 3, 1, r);
            assertTrue(id.equals("ctrcraft:turbo") || id.equals("mypack:banana"));
            if (id.equals("mypack:banana")) banana++;
        }
        assertTrue(banana > 2800 && banana < 3200, "banana " + banana);
        assertThrows(IllegalArgumentException.class, () -> PowerupTable.custom(Map.of("a:b", 0)));
        assertThrows(IllegalArgumentException.class, () -> RuleSet.CTR.withMaxSpeedPercent(150));
    }

    @Test
    void the_ctr_table_draws_from_the_place_based_sets() {
        Random r = new Random(1);
        for (int i = 0; i < 500; i++) {
            String id = PowerupTable.ctr().draw(8, 0, 1, r);
            assertTrue(java.util.Set.of("ctrcraft:potion", "ctrcraft:tnt", "ctrcraft:bomb", "ctrcraft:turbo", "ctrcraft:shield").contains(id), id);
        }
    }
}
