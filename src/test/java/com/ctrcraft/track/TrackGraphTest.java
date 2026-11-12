package com.ctrcraft.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ctrcraft.track.TrackGraph.Kind;
import com.ctrcraft.track.TrackGraph.LinkResult;

/** The rules of the node graph: links, branches, loops, one start per track. */
class TrackGraphTest {
    static TrackGraph square() {
        TrackGraph g = new TrackGraph();
        g.put(1, Kind.START, 0);
        g.put(2, Kind.CHECKPOINT, 4);
        g.put(3, Kind.CHECKPOINT, 8);
        g.put(4, Kind.CHECKPOINT, 12);
        return g;
    }

    @Test
    void linkingIsUndirectedAndTogglesOff() {
        TrackGraph g = square();
        assertEquals(LinkResult.LINKED, g.toggleLink(1, 2));
        assertTrue(g.get(1).links().contains(2L) && g.get(2).links().contains(1L), "both nodes know each other");
        assertEquals(LinkResult.UNLINKED, g.toggleLink(2, 1), "linking again from the other side removes it");
        assertTrue(g.get(1).links().isEmpty() && g.get(2).links().isEmpty());
    }

    @Test
    void aChainBackToTheStartClosesTheLoop() {
        TrackGraph g = square();
        g.toggleLink(1, 2);
        g.toggleLink(2, 3);
        g.toggleLink(3, 4);
        assertFalse(g.loopClosed(1), "an open chain is not a loop");
        assertEquals(LinkResult.LINKED, g.toggleLink(4, 1));
        assertTrue(g.loopClosed(1));
        TrackGraph.Summary s = g.summary(3);
        assertEquals(4, s.nodes());
        assertEquals(1, s.starts());
        assertEquals(3, s.checkpoints());
        assertTrue(s.loopClosed());
    }

    @Test
    void branchesLinkSeveralNodesToTheSameCheckpoint() {
        TrackGraph g = square();
        g.put(5, Kind.CHECKPOINT, 2);
        g.toggleLink(1, 2);
        g.toggleLink(2, 3);
        g.toggleLink(3, 4);
        g.toggleLink(4, 1);
        // a shortcut: 5 hangs off 2 and rejoins at 4
        assertEquals(LinkResult.LINKED, g.toggleLink(2, 5));
        assertEquals(LinkResult.LINKED, g.toggleLink(5, 4));
        assertEquals(3, g.get(2).links().size(), "2 is linked to 1, 3 and the branch");
        assertEquals(3, g.get(4).links().size());
        assertTrue(g.loopClosed(1));
        assertEquals(5, g.summary(5).nodes(), "the branch is part of the same track");
    }

    @Test
    void aTrackHasOnlyOneStart() {
        TrackGraph g = square();
        g.put(9, Kind.START, 0);
        g.toggleLink(1, 2);
        assertEquals(LinkResult.TWO_STARTS, g.toggleLink(2, 9), "joining another start to this track is refused");
        assertFalse(g.get(2).links().contains(9L));
        g.put(10, Kind.CHECKPOINT, 0);
        assertEquals(LinkResult.LINKED, g.toggleLink(9, 10), "but the other start can have its own track");
    }

    @Test
    void closingALoopInsideOneTrackIsNotATwoStartProblem() {
        TrackGraph g = square();
        g.toggleLink(1, 2);
        g.toggleLink(2, 3);
        assertEquals(LinkResult.LINKED, g.toggleLink(3, 1), "same component: fine even though it has a start");
    }

    @Test
    void removingANodeRemovesItsLinks() {
        TrackGraph g = square();
        g.toggleLink(1, 2);
        g.toggleLink(2, 3);
        g.remove(2);
        assertFalse(g.contains(2));
        assertTrue(g.get(1).links().isEmpty() && g.get(3).links().isEmpty());
        assertEquals(2, g.components().stream().filter(c -> c.size() == 1).count() - 1, "1 and 3 are separate again (4 is alone too)");
    }

    @Test
    void aCheckpointMayOnlyBecomeAStartIfItsTrackHasNone() {
        TrackGraph g = square();
        g.toggleLink(1, 2);
        g.toggleLink(2, 3);
        assertFalse(g.canBecome(2, Kind.START), "the track already has a start");
        assertTrue(g.canBecome(2, Kind.CHECKPOINT));
        assertTrue(g.canBecome(1, Kind.START), "the start itself may be put down again");
        assertTrue(g.canBecome(4, Kind.START), "an unlinked checkpoint is its own track");
    }

    @Test
    void replacingANodeKeepsItsLinks() {
        TrackGraph g = square();
        g.toggleLink(1, 2);
        g.toggleLink(2, 3);
        g.get(2).ext[NodeGeometry.LEFT] = 9.0F;
        g.put(2, Kind.CHECKPOINT, 7);                       // turned
        assertEquals(7, g.get(2).rotation);
        assertTrue(g.get(2).links().contains(1L) && g.get(2).links().contains(3L), "links survive");
        assertEquals(9.0F, g.get(2).ext[NodeGeometry.LEFT], "and so does the box size");
        assertTrue(g.get(1).links().contains(2L), "the neighbours still point to it");
    }

    @Test
    void selfAndMissingLinksAreRefused() {
        TrackGraph g = square();
        assertEquals(LinkResult.SAME_NODE, g.toggleLink(1, 1));
        assertEquals(LinkResult.MISSING, g.toggleLink(1, 99));
    }

    @Test
    void startOfFindsTheTracksStart() {
        TrackGraph g = square();
        g.toggleLink(1, 2);
        g.toggleLink(2, 3);
        assertEquals(1L, g.startOf(3));
        assertNull(g.startOf(4), "an unlinked checkpoint has no start");
    }

    @Test
    void theVersionChangesWhenTheGraphDoes() {
        TrackGraph g = new TrackGraph();
        int v0 = g.version();
        g.put(1, Kind.START, 0);
        assertTrue(g.version() > v0);
    }
}
