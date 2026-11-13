package com.ctrcraft.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ctrcraft.track.RaceProgress.Kind;

/** Laps, branches and the grid. Uses the same square course as the test drive tests. */
class RaceProgressTest {
    static final long S = TestDriveTest.S;
    static final long A = TestDriveTest.A;
    static final long B = TestDriveTest.B;
    static final long C = TestDriveTest.C;

    /** A recorded square, S A B C S, and a shortcut X between A and C that was driven too. */
    static RaceProgress course(boolean withShortcut, TrackRoutes[] out) {
        TrackGraph g = TestDriveTest.square();
        if (withShortcut) {
            g.put(TestDriveTest.X, TrackGraph.Kind.CHECKPOINT, 0);
            g.toggleLink(A, TestDriveTest.X);
            g.toggleLink(TestDriveTest.X, C);
        }
        TrackRoutes r = new TrackRoutes();
        TestDrive d = new TestDrive(g, r, S);
        d.reach(A);
        d.reach(B);
        d.reach(C);
        d.reach(S);
        if (withShortcut) {
            d.reach(TestDriveTest.X);
            d.reach(C);
        }
        out[0] = r;
        return new RaceProgress(g, r);
    }

    @Test
    void ringsInOrderCloseALap() {
        RaceProgress p = course(false, new TrackRoutes[1]);
        assertEquals(Set.of(A), p.expected());
        assertEquals(Kind.NODE, p.reach(A).kind());
        assertEquals(Set.of(B), p.expected());
        p.reach(B);
        p.reach(C);
        assertEquals(Kind.LAP, p.reach(S).kind());
        assertEquals(1, p.laps());
        assertEquals(Set.of(A), p.expected(), "round again");
    }

    @Test
    void aRingOutOfOrderDoesNothing() {
        RaceProgress p = course(false, new TrackRoutes[1]);
        assertEquals(Kind.NONE, p.reach(C).kind(), "skipping A and B");
        assertEquals(0.0, p.progress());
    }

    @Test
    void theShortcutCountsInsteadOfTheMainStretch() {
        RaceProgress p = course(true, new TrackRoutes[1]);
        p.reach(A);
        assertEquals(Set.of(B, TestDriveTest.X), p.expected(), "at A the main road or the shortcut");
        assertEquals(Kind.NODE, p.reach(TestDriveTest.X).kind());
        assertEquals(Set.of(C), p.expected(), "the shortcut rejoins at C");
        assertEquals(Kind.NODE, p.reach(C).kind());
        assertEquals(Kind.LAP, p.reach(S).kind(), "B was skipped by the shortcut");
        assertEquals(1, p.laps());
    }

    @Test
    void progressGrowsAndTheShortcutIsPaidPro() {
        RaceProgress p = course(true, new TrackRoutes[1]);
        double last = p.progress();
        for (long n : new long[] { A, TestDriveTest.X, C, S }) {
            p.reach(n);
            assertTrue(p.progress() > last, "after " + n);
            last = p.progress();
        }
    }

    @Test
    void aKartDrivingThroughTheRingsFinishesALap() {
        RaceProgress p = course(false, new TrackRoutes[1]);
        double[][] route = { { 2, 64.5, 0.5 }, { 38, 64.5, 0.5 }, { 43, 64.5, 0.5 }, { 40.5, 64.5, 38 }, { 40.5, 64.5, 43 },
                { 3, 64.5, 40.5 }, { -3, 64.5, 40.5 }, { 0.5, 64.5, 3 }, { 0.5, 64.5, -3 } };
        int laps = 0;
        for (int i = 1; i < route.length; i++) {
            double[] a = route[i - 1];
            double[] b = route[i];
            if (p.move(a[0], a[1], a[2], b[0], b[1], b[2]).kind() == Kind.LAP) laps++;
        }
        assertEquals(1, laps);
    }

    @Test
    void aRespawnGoesToTheLastRingPassed() {
        RaceProgress p = course(true, new TrackRoutes[1]);
        assertEquals(S, p.lastPassed(), "before any ring: the start");
        p.reach(A);
        assertEquals(A, p.lastPassed());
        p.reach(TestDriveTest.X);
        assertEquals(TestDriveTest.X, p.lastPassed(), "on the shortcut: its last ring");
        p.reach(C);
        assertEquals(C, p.lastPassed());
    }

    @Test
    void theLightsComeOnOneByOneAndStayUntilGo() {
        // DotLights.c: red lamps at 2880, 1920 and 960 ms left, the green (GO) at 0
        assertEquals(0, RaceGrid.lightsLit(RaceGrid.LIGHTS_TICKS + 5));
        assertEquals(0, RaceGrid.lightsLit(58));
        assertEquals(1, RaceGrid.lightsLit(57));
        assertEquals(1, RaceGrid.lightsLit(39));
        assertEquals(2, RaceGrid.lightsLit(38));
        assertEquals(2, RaceGrid.lightsLit(20));
        assertEquals(3, RaceGrid.lightsLit(19));
        assertEquals(3, RaceGrid.lightsLit(1));
    }

    @Test
    void theGridIsBehindTheStartNodeAndFacesTheWayItDoes() {
        long start = NodeGeometry.pack(100, 64, 100);
        RaceGrid.Slot pole = RaceGrid.slot(start, 12, 0);      // the node faces east: the grid is to its west
        assertTrue(pole.x() < 100.5, "behind: west of the node");
        assertEquals(270.0F, pole.yaw());
        RaceGrid.Slot second = RaceGrid.slot(start, 12, 1);
        assertEquals(pole.x(), second.x(), 0.3, "the second kart is beside the first, in the same row");
        assertEquals(3.77, Math.abs(second.z() - pole.z()), 0.02, "3.8 blocks along the row");
        RaceGrid.Slot fifth = RaceGrid.slot(start, 12, 4);
        assertEquals(5.0, pole.x() - fifth.x(), 0.2, "the second row is about 5 blocks further back");
    }

    @Test
    void theGridIsTheOriginalTwoRowsOfFourAndRepeatsBehind() {
        long start = NodeGeometry.pack(100, 64, 100);
        RaceGrid.Slot pole = RaceGrid.slot(start, 12, 0);
        RaceGrid.Slot last = RaceGrid.slot(start, 12, 3);
        assertEquals(11.31, Math.abs(pole.z() - last.z()), 0.02, "four karts a row, 3.8 blocks apart");
        // the 9th racer starts the pattern again behind the 8th
        RaceGrid.Slot ninth = RaceGrid.slot(start, 12, 8);
        assertTrue(ninth.x() < RaceGrid.slot(start, 12, 7).x(), "behind the last of the first eight");
        assertEquals(pole.z(), ninth.z(), 1e-9);
        // the whole pattern fits the gathering box
        for (int i = 0; i < 8; i++) {
            RaceGrid.Slot s = RaceGrid.slot(start, 12, i);
            assertTrue(GatherZone.contains(start, 12, s.x(), s.y() + 0.5, s.z()), "slot " + i + " is inside the gathering area");
        }
    }

    /** A branch across the start line on a course of four nodes: which end it is entered from follows the length of the stretch it replaces, not the node count. */
    @Test
    void aBranchAcrossTheStartLineIsEnteredFromTheNodeBeforeIt() {
        long s = TestDriveTest.at(0, 0);
        long a = TestDriveTest.at(100, 0);
        long b = TestDriveTest.at(100, 40);
        long c = TestDriveTest.at(0, 20);
        long y = TestDriveTest.at(50, -10);
        TrackGraph g = new TrackGraph();
        g.put(s, TrackGraph.Kind.START, 12);
        g.put(a, TrackGraph.Kind.CHECKPOINT, 0);
        g.put(b, TrackGraph.Kind.CHECKPOINT, 4);
        g.put(c, TrackGraph.Kind.CHECKPOINT, 8);
        g.put(y, TrackGraph.Kind.CHECKPOINT, 12);
        g.toggleLink(s, a);
        g.toggleLink(a, b);
        g.toggleLink(b, c);
        g.toggleLink(c, s);
        g.toggleLink(a, y);
        g.toggleLink(y, c);
        TrackRoutes r = new TrackRoutes();
        r.setMain(java.util.List.of(s, a, b, c, s));
        r.addBranch(new TrackRoutes.Branch(a, c, new long[] { y }));       // recorded the way the course does not go
        RaceProgress p = new RaceProgress(g, r);
        p.reach(a);
        p.reach(b);
        p.reach(c);
        assertTrue(p.expected().contains(y), "at C the branch over the start line is one of the ways on");
        assertEquals(Kind.NODE, p.reach(y).kind());
        assertEquals(Kind.LAP, p.reach(a).kind(), "it rejoins at A, past the start line: the lap counts");
        assertEquals(1, p.laps());
    }
}
