package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One driver's test drive of one track (see docs/DESIGN_TRACKS.md), pure Java. Feed it the kart's moves; it says which rings were passed.
 *
 * <p>MAIN phase: you drive from the start through the nodes; the rings shown are the unvisited nodes linked to the one you reached
 * last. Passing the start again with a loop behind you ends the lap, and that lap is stored as the main path.
 * BRANCHES phase (the main path exists): a ring shows where each undriven branch begins (a node off the main path linked to it).
 * Passing one starts a branch; you follow it node by node and it ends when you pass a main-path node it is linked to.
 */
public final class TestDrive {
    public enum Phase { MAIN, BRANCHES }

    public enum Kind { NONE, NODE, LAP, BRANCH_START, BRANCH_END }

    public record Result(Kind kind, long node) {
        static final Result NONE = new Result(Kind.NONE, 0);
    }

    private final TrackGraph graph;
    private final TrackRoutes routes;
    private final long start;
    private int seenVersion;

    /** The lap so far in MAIN, starting at the start node. */
    private final List<Long> path = new ArrayList<>();
    /** The branch so far in BRANCHES (only its own nodes). */
    private final List<Long> branch = new ArrayList<>();

    public TestDrive(TrackGraph graph, TrackRoutes routes, long start) {
        this.graph = graph;
        this.routes = routes;
        this.start = start;
        this.seenVersion = graph.version();
        reset();
    }

    public long start() {
        return start;
    }

    public TrackRoutes routes() {
        return routes;
    }

    public Phase phase() {
        return routes.hasMain() ? Phase.BRANCHES : Phase.MAIN;
    }

    /** Forgets the lap or branch in progress (the stored routes stay). */
    public void reset() {
        path.clear();
        branch.clear();
        path.add(start);
    }

    /** Forgets the stored routes too, to record the main lap again. */
    public void rerecord() {
        routes.clear();
        reset();
    }

    /** What you have driven in the current lap or branch, for drawing a trail. */
    public List<Long> trail() {
        return phase() == Phase.MAIN ? List.copyOf(path) : List.copyOf(branch);
    }

    public boolean inBranch() {
        return !branch.isEmpty();
    }

    public int lapNodes() {
        return path.size() - 1;
    }

    public boolean valid() {
        return routes.valid(graph, start);
    }

    public int uncovered() {
        return routes.uncovered(graph, start).size();
    }

    /**
     * Call when the graph may have changed. Returns false if the start node is gone (the drive is over); otherwise drops stored
     * routes that no longer fit and restarts a lap or branch whose nodes or links changed.
     */
    public boolean refresh() {
        if (graph.version() == seenVersion) return true;
        seenVersion = graph.version();
        if (!graph.contains(start) || graph.get(start).kind != TrackGraph.Kind.START) return false;
        routes.verify(graph, start);
        boolean ok = true;
        for (int i = 0; i < path.size(); i++) {
            long p = path.get(i);
            if (!graph.contains(p) || (i > 0 && !graph.get(p).links().contains(path.get(i - 1)))) ok = false;
        }
        long prev = -1;
        for (long b : branch) {
            if (!graph.contains(b) || (prev != -1 && !graph.get(b).links().contains(prev))) ok = false;
            prev = b;
        }
        if (!ok || (phase() == Phase.MAIN && !branch.isEmpty())) reset();
        return true;
    }

    /** The nodes whose rings are shown now. */
    public Set<Long> candidates() {
        Set<Long> out = new LinkedHashSet<>();
        if (phase() == Phase.MAIN) {
            long cur = path.get(path.size() - 1);
            for (long l : graph.get(cur).links()) {
                if (l == start ? path.size() >= 3 : !path.contains(l)) out.add(l);
            }
            return out;
        }
        Set<Long> main = routes.mainSet();
        if (branch.isEmpty()) {
            Set<Long> uncovered = routes.uncovered(graph, start);
            for (long m : main) {
                for (long l : graph.get(m).links()) {
                    if (main.contains(l)) continue;
                    if (uncovered.contains(l) || hasUncoveredOffMainNeighbour(l, main, uncovered)) out.add(l);
                }
            }
            return out;
        }
        long last = branch.get(branch.size() - 1);
        for (long l : graph.get(last).links()) {
            if (main.contains(l)) {
                // a single node needs two different main nodes to be a branch between them
                if (branch.size() > 1 || mainNeighbours(branch.get(0), main).size() > 1) out.add(l);
            } else if (!branch.contains(l)) {
                out.add(l);
            }
        }
        return out;
    }

    private boolean hasUncoveredOffMainNeighbour(long node, Set<Long> main, Set<Long> uncovered) {
        for (long l : graph.get(node).links()) {
            if (!main.contains(l) && uncovered.contains(l)) return true;
        }
        return false;
    }

    private List<Long> mainNeighbours(long node, Set<Long> main) {
        List<Long> out = new ArrayList<>();
        for (long l : graph.get(node).links()) if (main.contains(l)) out.add(l);
        return out;
    }

    /** One kart move, from the previous tick's position to this one's. At most one ring counts per move (the one passed first). */
    public Result move(double x0, double y0, double z0, double x1, double y1, double z1) {
        long hit = 0;
        double hitT = Double.MAX_VALUE;
        for (long c : candidates()) {
            TrackGraph.Node n = graph.get(c);
            double t = NodeGeometry.touch(c, n.rotation, n.ext, x0, y0, z0, x1, y1, z1);
            if (t >= 0 && t < hitT) {
                hitT = t;
                hit = c;
            }
        }
        if (hitT == Double.MAX_VALUE) return Result.NONE;
        return reach(hit);
    }

    /** Applies "the driver passed the ring of {@code node}" (a candidate). Public so a test or a command can drive without positions. */
    public Result reach(long node) {
        if (phase() == Phase.MAIN) {
            path.add(node);
            if (node == start) {
                routes.setMain(path);
                path.clear();
                path.add(start);
                return new Result(Kind.LAP, node);
            }
            return new Result(Kind.NODE, node);
        }
        Set<Long> main = routes.mainSet();
        if (branch.isEmpty()) {
            branch.add(node);
            return new Result(Kind.BRANCH_START, node);
        }
        if (main.contains(node)) {
            long[] nodes = branch.stream().mapToLong(Long::longValue).toArray();
            long to = node;
            long from = -1;
            // the main node the branch left from: a main neighbour of its first node, a different one than where it rejoined if possible
            for (long m : mainNeighbours(nodes[0], main)) {
                if (from == -1) from = m;
                if (m != to) {
                    from = m;
                    break;
                }
            }
            routes.addBranch(new TrackRoutes.Branch(from, to, nodes));
            branch.clear();
            return new Result(Kind.BRANCH_END, node);
        }
        branch.add(node);
        return new Result(Kind.NODE, node);
    }
}
