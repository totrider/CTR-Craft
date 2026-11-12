package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One racer's progress round a recorded track (the main path of {@link TrackRoutes}), pure Java. The racer has to pass the rings of the
 * main path in order, or take a recorded branch instead of the stretch of main path it replaces (rings of the branch in order, then it
 * rejoins the main path at its far end, and the skipped main nodes count as passed). Passing the start ring again closes a lap.
 *
 * <p>Which way round a branch runs is decided by the main path: it is entered at the end from which the rejoining end is closer
 * forwards along the course. A branch that runs past the start line (enters before it, rejoins after it) counts the lap when it rejoins.
 */
public final class RaceProgress {
    public enum Kind { NONE, NODE, LAP }

    public record Result(Kind kind, long node) {
        static final Result NONE = new Result(Kind.NONE, 0);
    }

    /** A branch seen from the direction of the course. */
    private record Fwd(int entryIdx, int exitIdx, long[] chain, long exit) {}

    private final TrackGraph graph;
    private final List<Long> main;
    private final int n;
    private final List<Fwd> branches = new ArrayList<>();

    private int at;                  // index in main of the last main node passed (0: the start line)
    private int laps;                // laps completed
    private Fwd current;             // the branch being driven, or null
    private int branchPos;           // next ring in current.chain; == chain.length means the exit is next

    public RaceProgress(TrackGraph graph, TrackRoutes routes) {
        this.graph = graph;
        this.main = routes.main();
        this.n = main.size() - 1;
        Map<Long, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.putIfAbsent(main.get(i), i);
        for (TrackRoutes.Branch b : routes.branches()) {
            Integer from = idx.get(b.from());
            Integer to = idx.get(b.to());
            if (from == null || to == null) continue;
            int d = Math.floorMod(to - from, n);
            if (d * 2 <= n) {
                branches.add(new Fwd(from, to, b.nodes(), b.to()));
            } else {
                long[] rev = new long[b.nodes().length];
                for (int i = 0; i < rev.length; i++) rev[i] = b.nodes()[rev.length - 1 - i];
                branches.add(new Fwd(to, from, rev, b.from()));
            }
        }
    }

    public int laps() {
        return laps;
    }

    /** Rings passed so far, as a number that only grows: laps times the nodes of a lap, plus where on this lap, branches counted pro rata. */
    public double progress() {
        double p = (double) laps * n + at;
        if (current != null) {
            int span = Math.max(1, Math.floorMod(current.exitIdx() - current.entryIdx(), n));
            p += (double) branchPos / (current.chain().length + 1) * span;
        }
        return p;
    }

    /** The last ring passed: where a respawn puts you (the main node you were last at, or the last ring of the branch you are on). */
    public long lastPassed() {
        return current != null && branchPos >= 1 ? current.chain()[branchPos - 1] : main.get(at);
    }

    /** The rings that count next. */
    public Set<Long> expected() {
        Set<Long> out = new LinkedHashSet<>();
        if (current != null) {
            out.add(branchPos < current.chain().length ? current.chain()[branchPos] : current.exit());
            return out;
        }
        out.add(main.get(at + 1));
        for (Fwd f : branches) {
            if (f.entryIdx() == at) out.add(f.chain()[0]);
        }
        return out;
    }

    /** The squared distance (blocks, in the ground plane) to the nearest ring that counts next: breaks ties between racers on the same ring. */
    public double distanceToNextSq(double x, double z) {
        double best = Double.MAX_VALUE;
        for (long e : expected()) {
            double dx = NodeGeometry.x(e) + 0.5 - x;
            double dz = NodeGeometry.z(e) + 0.5 - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    /** One kart move. At most one ring counts per move (the first passed). */
    public Result move(double x0, double y0, double z0, double x1, double y1, double z1) {
        long hit = 0;
        double hitT = Double.MAX_VALUE;
        for (long c : expected()) {
            TrackGraph.Node node = graph.get(c);
            if (node == null) continue;
            double t = NodeGeometry.touch(c, node.rotation, node.ext, x0, y0, z0, x1, y1, z1);
            if (t >= 0 && t < hitT) {
                hitT = t;
                hit = c;
            }
        }
        return hitT == Double.MAX_VALUE ? Result.NONE : reach(hit);
    }

    /** Applies "the racer passed the ring of {@code node}" (one of {@link #expected()}). */
    public Result reach(long node) {
        if (current != null) {
            if (branchPos < current.chain().length) {
                if (node != current.chain()[branchPos]) return Result.NONE;
                branchPos++;
                return new Result(Kind.NODE, node);
            }
            if (node != current.exit()) return Result.NONE;
            boolean wrapped = current.exitIdx() < current.entryIdx();
            at = current.exitIdx();
            current = null;
            if (wrapped) {
                laps++;
                return new Result(Kind.LAP, node);
            }
            return new Result(Kind.NODE, node);
        }
        if (node == main.get(at + 1)) {
            at++;
            if (at == n) {
                at = 0;
                laps++;
                return new Result(Kind.LAP, node);
            }
            return new Result(Kind.NODE, node);
        }
        for (Fwd f : branches) {
            if (f.entryIdx() == at && f.chain()[0] == node) {
                current = f;
                branchPos = 1;
                return new Result(Kind.NODE, node);
            }
        }
        return Result.NONE;
    }
}
