package com.ctrcraft.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The gathering box behind a Start node, and the distance to a track's line. */
class GatherZoneTest {
    private static final long START = NodeGeometry.pack(100, 64, 100);

    private static double[] at(int rotation, double along, double across) {
        double[] p = GatherZone.point(START, rotation, along, across);
        return new double[] { p[0], 64.5, p[1] };
    }

    @Test
    void the_box_is_behind_the_line_and_a_fixed_size() {
        for (int rot = 0; rot < 16; rot++) {
            double[] in = at(rot, 8, 3);
            assertTrue(GatherZone.contains(START, rot, in[0], in[1], in[2]), "rotation " + rot);
            double[] ahead = at(rot, -2, 0);
            assertFalse(GatherZone.contains(START, rot, ahead[0], ahead[1], ahead[2]), "in front, rotation " + rot);
            double[] far = at(rot, 17, 0);
            assertFalse(GatherZone.contains(START, rot, far[0], far[1], far[2]), "too deep, rotation " + rot);
            double[] side = at(rot, 8, 9);
            assertFalse(GatherZone.contains(START, rot, side[0], side[1], side[2]), "too wide, rotation " + rot);
        }
    }

    @Test
    void the_box_has_a_height() {
        double[] p = at(0, 8, 0);
        assertTrue(GatherZone.contains(START, 0, p[0], 64.5 + 4, p[2]));
        assertFalse(GatherZone.contains(START, 0, p[0], 64.5 + 6, p[2]));
        assertFalse(GatherZone.contains(START, 0, p[0], 64.5 - 4, p[2]));
    }

    @Test
    void a_point_is_measured_to_the_nearest_part_of_a_segment() {
        assertEquals(25.0, NodeGeometry.segmentDistSq(5, 5, 0, 0, 0, 0, 10, 0, 0), 1e-9);   // beside the middle
        assertEquals(50.0, NodeGeometry.segmentDistSq(-5, 5, 0, 0, 0, 0, 10, 0, 0), 1e-9);  // past the end: to the end point
        assertEquals(9.0, NodeGeometry.segmentDistSq(3, 0, 0, 0, 0, 0, 0, 0, 0), 1e-9);     // a point
    }

    @Test
    void a_crate_beside_a_long_link_is_near_the_track_even_far_from_both_nodes() {
        TrackGraph g = new TrackGraph();
        long a = NodeGeometry.pack(0, 64, 0);
        long b = NodeGeometry.pack(200, 64, 0);
        g.put(a, TrackGraph.Kind.START, 0);
        g.put(b, TrackGraph.Kind.CHECKPOINT, 0);
        g.toggleLink(a, b);
        // 100 blocks from either node, 20 from the line between them
        double d = g.distSqToTrack(a, 100.5, 64.5, 20.5);
        assertTrue(d < 21 * 21, "distance squared " + d);
        assertTrue(d > 19 * 19);
    }
}
