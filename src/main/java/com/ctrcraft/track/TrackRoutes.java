package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a test drive recorded for one track (keyed by its start node): the MAIN PATH, the first complete lap, as an ordered list
 * {@code [start, c1, c2, ..., start]}, and the BRANCHES, each a chain of nodes off the main path that rejoins it. Pure Java.
 *
 * <p>The graph can change after a drive (nodes broken, links changed): {@link #verify} drops whatever no longer matches, so a stored
 * route is never wrong, only sometimes gone.
 */
public final class TrackRoutes {
    /** A way round part of the main path: leaves it at {@code from}, goes through {@code nodes} (none of them on the main path), rejoins at {@code to}. */
    public record Branch(long from, long to, long[] nodes) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Branch b && from == b.from && to == b.to && Arrays.equals(nodes, b.nodes);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Long.hashCode(from) + Long.hashCode(to)) + Arrays.hashCode(nodes);
        }

        /** The same branch driven the other way round. */
        Branch reversed() {
            long[] r = new long[nodes.length];
            for (int i = 0; i < r.length; i++) r[i] = nodes[r.length - 1 - i];
            return new Branch(to, from, r);
        }
    }

    /** How many laps a race on this track runs (a setting of the track, kept when the routes are re-recorded). */
    private int laps = 3;
    private List<Long> main = List.of();
    private final List<Branch> branches = new ArrayList<>();

    public int laps() {
        return laps;
    }

    public void setLaps(int laps) {
        this.laps = Math.max(1, Math.min(99, laps));
    }

    public boolean hasMain() {
        return !main.isEmpty();
    }

    public List<Long> main() {
        return Collections.unmodifiableList(main);
    }

    public List<Branch> branches() {
        return Collections.unmodifiableList(branches);
    }

    public void setMain(List<Long> path) {
        main = List.copyOf(path);
        branches.clear();       // branches hang off the old main path
    }

    public void clear() {
        main = List.of();
        branches.clear();
    }

    /** Adds a branch unless the same one (in either direction) is already there. Returns whether it was new. */
    public boolean addBranch(Branch b) {
        if (branches.contains(b) || branches.contains(b.reversed())) return false;
        branches.add(b);
        return true;
    }

    /** The nodes of the main path, each once. */
    public Set<Long> mainSet() {
        return new LinkedHashSet<>(main);
    }

    /** Drops the recording (or the branches) that no longer fits the graph. */
    public void verify(TrackGraph g, long start) {
        if (!hasMain()) return;
        boolean ok = main.size() >= 4 && main.get(0) == start && main.get(main.size() - 1) == start && g.contains(start);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; ok && i < main.size(); i++) {
            long p = main.get(i);
            if (!g.contains(p) || (i < main.size() - 1 && !seen.add(p))) ok = false;
            if (ok && i > 0 && !g.get(p).links().contains(main.get(i - 1))) ok = false;
        }
        if (!ok) {
            clear();
            return;
        }
        Set<Long> onMain = mainSet();
        branches.removeIf(b -> !branchFits(g, onMain, b));
    }

    private static boolean branchFits(TrackGraph g, Set<Long> onMain, Branch b) {
        if (b.nodes().length == 0 || !onMain.contains(b.from()) || !onMain.contains(b.to())) return false;
        long prev = b.from();
        Set<Long> seen = new HashSet<>();
        for (long n : b.nodes()) {
            if (onMain.contains(n) || !seen.add(n) || !g.contains(n) || !g.get(n).links().contains(prev)) return false;
            prev = n;
        }
        return g.get(b.to()).links().contains(prev);
    }

    /** Nodes of the start's track that no recorded route passes through. */
    public Set<Long> uncovered(TrackGraph g, long start) {
        Set<Long> left = new LinkedHashSet<>(g.component(start));
        left.removeAll(main);
        for (Branch b : branches) for (long n : b.nodes()) left.remove(n);
        return left;
    }

    /** A track is valid once a full lap is recorded and every node of it lies on the main path or on a driven branch. */
    public boolean valid(TrackGraph g, long start) {
        return hasMain() && uncovered(g, start).isEmpty();
    }

    /**
     * How many nodes of the main path face more than a quarter turn away from the way the course goes through them. It is only a
     * warning (a ring works from either side), but a start grid or a wrong-way check later will use the facing.
     */
    public int facingAgainstCourse(TrackGraph g) {
        int bad = 0;
        int n = main.size() - 1;                // the closing start repeats the first
        for (int i = 0; i < n; i++) {
            long prev = main.get((i + n - 1) % n);
            long next = main.get((i + 1) % n);
            TrackGraph.Node node = g.get(main.get(i));
            if (node == null) continue;
            double dx = NodeGeometry.x(next) - NodeGeometry.x(prev);
            double dz = NodeGeometry.z(next) - NodeGeometry.z(prev);
            if (dx * NodeGeometry.facingX(node.rotation) + dz * NodeGeometry.facingZ(node.rotation) < 0) bad++;
        }
        return bad;
    }
}
