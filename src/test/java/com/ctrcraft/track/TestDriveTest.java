package com.ctrcraft.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ctrcraft.track.TestDrive.Kind;
import com.ctrcraft.track.TestDrive.Phase;

/** The test drive: ring crossings, the main lap, branches, validity. */
class TestDriveTest {
    static long at(int x, int z) {
        return NodeGeometry.pack(x, 64, z);
    }

    // a square course, 40 blocks a side, start in the south-west corner, all nodes facing along the course
    static final long S = at(0, 0);
    static final long A = at(40, 0);
    static final long B = at(40, 40);
    static final long C = at(0, 40);
    // a shortcut node between A and C... off the loop: linked to A and C
    static final long X = at(20, 20);

    static TrackGraph square() {
        TrackGraph g = new TrackGraph();
        g.put(S, TrackGraph.Kind.START, 8);         // faces north (the lap closes coming down the west side)
        g.put(A, TrackGraph.Kind.CHECKPOINT, 12);    // faces east
        g.put(B, TrackGraph.Kind.CHECKPOINT, 0);     // faces south
        g.put(C, TrackGraph.Kind.CHECKPOINT, 4);     // faces west
        g.toggleLink(S, A);
        g.toggleLink(A, B);
        g.toggleLink(B, C);
        g.toggleLink(C, S);
        return g;
    }

    @Test
    void packingRoundTripsIncludingNegatives() {
        long k = NodeGeometry.pack(-123, 70, -4567);
        assertEquals(-123, NodeGeometry.x(k));
        assertEquals(70, NodeGeometry.y(k));
        assertEquals(-4567, NodeGeometry.z(k));
    }

    @Test
    void aMoveThatTouchesTheBoxCountsAndOneBesideItDoesNot() {
        // S faces east (rotation 12): the default box reaches 3 blocks either side of x=0.5 and 12 to each side across it
        float[] e = NodeGeometry.DEFAULT_EXTENTS;
        double hit = NodeGeometry.touch(S, 12, e, -6, 64.5, 0.5, -1, 64.5, 0.5);
        assertEquals(0.7, hit, 1e-9, "enters the box 3.5 blocks into a 5 block move");
        assertEquals(0.0, NodeGeometry.touch(S, 12, e, 0.5, 64.5, 0.5, 1, 64.5, 0.5), "starting inside is an immediate touch");
        assertTrue(NodeGeometry.touch(S, 12, e, -2, 64.5, 15.0, 2, 64.5, 15.0) < 0, "passed 15 blocks to the side: outside the 12");
        assertTrue(NodeGeometry.touch(S, 12, e, -2, 80.0, 0.5, 2, 80.0, 0.5) < 0, "far above it");
        assertTrue(NodeGeometry.touch(S, 12, e, -9, 64.5, 0.5, -5, 64.5, 0.5) < 0, "stopped before it");
        assertTrue(NodeGeometry.touch(S, 12, e, 2, 64.5, 0.5, -9, 64.5, 0.5) >= 0, "the other way round counts too");
        assertTrue(NodeGeometry.touch(S, 12, e, -40, 64.5, 0.5, 40, 64.5, 0.5) >= 0, "a move that jumps clean over it still passes through");
    }

    @Test
    void aSideCanBeExtendedAndRetracted() {
        TrackGraph g = square();
        assertEquals(13.0F, g.resize(S, NodeGeometry.LEFT, 1));
        assertEquals(1.0F, g.resize(S, NodeGeometry.FRONT, -50), "never below one block");
        assertEquals(64.0F, g.resize(S, NodeGeometry.UP, 100), "or above 64");
        assertEquals(-1.0F, g.resize(at(5, 5), NodeGeometry.LEFT, 1), "no such node");
    }

    @Test
    void aRayFindsTheFaceItMeetsFromOutsideAndFromInside() {
        float[] e = NodeGeometry.DEFAULT_EXTENTS;
        // S faces east: front is +x, left is -z. From 10 blocks west of it, looking east, the first face is the back one, 7.5 blocks away
        NodeGeometry.FaceHit out = NodeGeometry.rayFace(S, 12, e, -10, 64.5, 0.5, 1, 0, 0, 64);
        assertEquals(NodeGeometry.BACK, out.side());
        assertEquals(7.5, out.distance(), 1e-9);
        // from the middle, looking east: the front face, 3 blocks away; looking along the left wall: the left face
        assertEquals(NodeGeometry.FRONT, NodeGeometry.rayFace(S, 12, e, 0.5, 64.5, 0.5, 1, 0, 0, 64).side());
        assertEquals(NodeGeometry.LEFT, NodeGeometry.rayFace(S, 12, e, 0.5, 64.5, 0.5, 0, 0, -1, 64).side());
        assertEquals(NodeGeometry.UP, NodeGeometry.rayFace(S, 12, e, 0.5, 64.5, 0.5, 0, 1, 0, 64).side());
        assertTrue(NodeGeometry.rayFace(S, 12, e, -10, 64.5, 0.5, -1, 0, 0, 64) == null, "looking away");
        assertTrue(NodeGeometry.rayFace(S, 12, e, -10, 64.5, 0.5, 1, 0, 0, 5) == null, "too far for the reach");
    }

    @Test
    void theMainLapIsRecordedOnceTheLoopClosesAtTheStart() {
        TrackGraph g = square();
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        assertEquals(Phase.MAIN, d.phase());
        assertEquals(java.util.Set.of(A, C), d.candidates(), "both neighbours of the start can be the first node");

        assertEquals(Kind.NODE, d.reach(A).kind());
        assertEquals(java.util.Set.of(B), d.candidates(), "no going back, only onwards");
        d.reach(B);
        d.reach(C);
        assertEquals(java.util.Set.of(S), d.candidates(), "only the start is left");
        TestDrive.Result res = d.reach(S);
        assertEquals(Kind.LAP, res.kind());
        assertEquals(List.of(S, A, B, C, S), r.main());
        assertEquals(Phase.BRANCHES, d.phase());
        assertTrue(d.valid(), "every node is on the lap");
        assertEquals(0, r.facingAgainstCourse(g), "all four face along the way round");
    }

    @Test
    void theStartCannotCloseAfterOnlyOneNode() {
        TrackGraph g = square();
        TestDrive d = new TestDrive(g, new TrackRoutes(), S);
        d.reach(A);
        assertFalse(d.candidates().contains(S), "start-A-start is going back along the same link, not a lap");
    }

    @Test
    void drivingThroughTheRingsOfARealPathRecordsIt() {
        TrackGraph g = square();
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        // one position per tick: out along the south edge, up the east edge, back along the north edge, down the west edge to the start
        double[][] route = { { 2, 64.5, 0.5 }, { 38, 64.5, 0.5 }, { 43, 64.5, 0.5 }, { 40.5, 64.5, 38 }, { 40.5, 64.5, 43 },
                { 3, 64.5, 40.5 }, { -3, 64.5, 40.5 }, { 0.5, 64.5, 3 }, { 0.5, 64.5, -3 } };
        int nodes = 0;
        int laps = 0;
        for (int i = 1; i < route.length; i++) {
            double[] a = route[i - 1];
            double[] b = route[i];
            TestDrive.Result res = d.move(a[0], a[1], a[2], b[0], b[1], b[2]);
            if (res.kind() == Kind.NODE) nodes++;
            if (res.kind() == Kind.LAP) laps++;
        }
        assertEquals(3, nodes, "A, B and C");
        assertEquals(1, laps);
        assertEquals(List.of(S, A, B, C, S), r.main());
    }

    @Test
    void aMoveThatMissesTheRingChangesNothing() {
        TestDrive d = new TestDrive(square(), new TrackRoutes(), S);
        // passes A's plane 20 blocks to the side of it
        assertEquals(Kind.NONE, d.move(38, 64.5, 20, 43, 64.5, 20).kind());
        assertEquals(0, d.lapNodes());
    }

    @Test
    void aBranchIsRecordedBetweenTwoMainNodesAndMakesTheTrackValid() {
        TrackGraph g = square();
        g.put(X, TrackGraph.Kind.CHECKPOINT, 0);
        g.toggleLink(A, X);
        g.toggleLink(X, C);
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        d.reach(A);
        d.reach(B);
        d.reach(C);
        d.reach(S);
        assertFalse(d.valid(), "the shortcut node is not driven yet");
        assertEquals(1, d.uncovered());
        assertEquals(java.util.Set.of(X), d.candidates(), "the ring of the branch start shows");

        assertEquals(Kind.BRANCH_START, d.reach(X).kind());
        assertTrue(d.inBranch());
        assertEquals(java.util.Set.of(A, C), d.candidates(), "it can rejoin at either main node it is linked to");
        assertEquals(Kind.BRANCH_END, d.reach(C).kind());
        assertEquals(1, r.branches().size());
        TrackRoutes.Branch b = r.branches().get(0);
        assertEquals(C, b.to());
        assertEquals(A, b.from(), "it left from the other main neighbour");
        assertTrue(d.valid());
        assertTrue(d.candidates().isEmpty(), "nothing left to drive");
    }

    @Test
    void theSameBranchDrivenTheOtherWayIsNotAddedTwice() {
        TrackGraph g = square();
        g.put(X, TrackGraph.Kind.CHECKPOINT, 0);
        g.toggleLink(A, X);
        g.toggleLink(X, C);
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        d.reach(A);
        d.reach(B);
        d.reach(C);
        d.reach(S);
        d.reach(X);
        d.reach(C);
        d.reach(X);
        d.reach(A);
        assertEquals(1, r.branches().size());
    }

    @Test
    void aLongerBranchMustBeDrivenNodeByNode() {
        TrackGraph g = square();
        long y1 = at(10, 20);
        long y2 = at(30, 20);
        g.put(y1, TrackGraph.Kind.CHECKPOINT, 0);
        g.put(y2, TrackGraph.Kind.CHECKPOINT, 0);
        g.toggleLink(S, y1);
        g.toggleLink(y1, y2);
        g.toggleLink(y2, B);
        TestDrive d = new TestDrive(g, new TrackRoutes(), S);
        d.reach(A);
        d.reach(B);
        d.reach(C);
        d.reach(S);
        assertEquals(java.util.Set.of(y1, y2), d.candidates(), "it can be entered from either end");
        d.reach(y1);
        assertEquals(java.util.Set.of(y2), d.candidates(), "S is where it left from, so only onwards");
        d.reach(y2);
        assertEquals(Kind.BRANCH_END, d.reach(B).kind());
        assertEquals(1, d.routes().branches().size());
        assertEquals(2, d.routes().branches().get(0).nodes().length);
        assertTrue(d.valid());
    }

    @Test
    void breakingAMainNodeDropsTheRecordingAndRestartsTheLap() {
        TrackGraph g = square();
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        d.reach(A);
        d.reach(B);
        d.reach(C);
        d.reach(S);
        assertTrue(r.hasMain());
        g.remove(B);
        assertTrue(d.refresh());
        assertFalse(r.hasMain(), "the lap went through B");
        assertEquals(Phase.MAIN, d.phase());
    }

    @Test
    void theDriveEndsWhenTheStartIsGone() {
        TrackGraph g = square();
        TestDrive d = new TestDrive(g, new TrackRoutes(), S);
        g.remove(S);
        assertFalse(d.refresh());
    }

    @Test
    void aNodeFacingAgainstTheCourseIsCounted() {
        TrackGraph g = square();
        g.put(B, TrackGraph.Kind.CHECKPOINT, 12);      // faces east, the course there goes west
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        d.reach(A);
        d.reach(B);
        d.reach(C);
        d.reach(S);
        assertEquals(1, r.facingAgainstCourse(g));
    }

    @Test
    void rerecordingClearsTheStoredRoutes() {
        TrackGraph g = square();
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        d.reach(A);
        d.reach(B);
        d.reach(C);
        d.reach(S);
        d.rerecord();
        assertFalse(r.hasMain());
        assertEquals(Phase.MAIN, d.phase());
    }
}
