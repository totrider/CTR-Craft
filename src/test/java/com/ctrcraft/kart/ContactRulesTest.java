package com.ctrcraft.kart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ctrcraft.physics.KartBump;
import com.ctrcraft.physics.KartBump.Other;
import com.ctrcraft.physics.V3;

/** The mod's own kart-versus-entity rules (not CTR's). */
class ContactRulesTest {
    @Test
    void weights_follow_the_size_of_the_body() {
        int cow = ContactRules.weight(0.9, 1.4);
        int zombie = ContactRules.weight(0.6, 1.95);
        int golem = ContactRules.weight(1.4, 2.7);
        assertTrue(zombie < cow && cow < golem);
        assertEquals(64, ContactRules.weight(0.1, 0.1));
    }

    @Test
    void a_slow_touch_is_harmless_and_top_speed_hurts() {
        assertEquals(0f, ContactRules.damage(5.0, 1.0));
        double top = ContactRules.blocksPerSecond(13140);
        assertEquals(31.0, top, 1.0);
        assertEquals(12.5f, ContactRules.damage(top, 1.0), 0.6f);
        assertEquals(0f, ContactRules.damage(top, 0.0));
    }

    @Test
    void a_heavy_entity_is_thrown_less_and_there_is_a_cap() {
        assertTrue(ContactRules.knockback(20, 256, 64) > ContactRules.knockback(20, 256, 1000));
        assertEquals(ContactRules.MAX_KNOCKBACK, ContactRules.knockback(1000, 256, 64));
    }

    @Test
    void the_kart_bounces_off_an_entity_it_reaches_and_reports_it() {
        double reach = ContactRules.reach(0.9);
        Other cow = new Other(new V3(0, 0, 40), V3.ZERO, ContactRules.weight(0.9, 1.4), reach, 77);
        KartBump.Result r = KartBump.resolve(V3.ZERO, new V3(0, 0, 6000), 256, List.of(cow));
        assertTrue(r.hit());
        assertEquals(77, r.id());
        assertTrue(r.impact() > 0);
        assertTrue(r.vel().z < 6000);
        // out of reach: nothing
        Other far = new Other(new V3(0, 0, reach + 1), V3.ZERO, 256, reach, 78);
        assertTrue(!KartBump.resolve(V3.ZERO, new V3(0, 0, 6000), 256, List.of(far)).hit());
    }
}
