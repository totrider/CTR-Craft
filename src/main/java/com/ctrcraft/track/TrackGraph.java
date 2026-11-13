package com.ctrcraft.track;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The track node graph: start and checkpoint nodes, and the links between them. Pure Java (no Minecraft imports): nodes are keyed by
 * a long (a packed block position), so the rules can be unit-tested. See docs/DESIGN_TRACKS.md.
 *
 * <p>Links are undirected: you link nodes without choosing a direction, the direction of travel comes from the test drive later.
 * A connected group of nodes is one track; a track has at most one start node.
 */
public final class TrackGraph {
    public enum Kind { START, CHECKPOINT }

    public static final class Node {
        public final Kind kind;
        /** 0-15 in 22.5 degree steps (the standing sign rotation); the direction the node faces. */
        public final int rotation;
        /** The box a kart has to touch to pass this node, see {@link NodeGeometry}: six extents in blocks, adjusted with the Track Tool. */
        public final float[] ext = NodeGeometry.DEFAULT_EXTENTS.clone();
        final Set<Long> links = new LinkedHashSet<>();

        Node(Kind kind, int rotation) {
            this.kind = kind;
            this.rotation = rotation & 15;
        }

        public Set<Long> links() {
            return Collections.unmodifiableSet(links);
        }
    }

    public enum LinkResult { LINKED, UNLINKED, SAME_NODE, MISSING, TWO_STARTS }

    /** What a node's track looks like right now: shown to the player when they link. */
    public record Summary(int nodes, int starts, int checkpoints, boolean loopClosed) {}

    private final Map<Long, Node> nodes = new LinkedHashMap<>();
    private int version;

    /** Bumped on every change: clients holding the tool are re-sent the graph when it differs. */
    public int version() {
        return version;
    }

    public Map<Long, Node> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    /**
     * The squared distance from a point to the track the node {@code any} belongs to, taken as the lines that link its nodes (a node with no
     * link counts as a point): a rough racing line, so a place beside a long straight is as near as one beside a node.
     */
    public double distSqToTrack(long any, double x, double y, double z) {
        double best = Double.MAX_VALUE;
        for (long p : component(any)) {
            Node n = nodes.get(p);
            if (n == null) continue;
            double ax = NodeGeometry.x(p) + 0.5;
            double ay = NodeGeometry.y(p) + 0.5;
            double az = NodeGeometry.z(p) + 0.5;
            best = Math.min(best, NodeGeometry.segmentDistSq(x, y, z, ax, ay, az, ax, ay, az));
            for (long q : n.links()) {
                best = Math.min(best, NodeGeometry.segmentDistSq(x, y, z, ax, ay, az,
                        NodeGeometry.x(q) + 0.5, NodeGeometry.y(q) + 0.5, NodeGeometry.z(q) + 0.5));
            }
        }
        return best;
    }

    public Node get(long pos) {
        return nodes.get(pos);
    }

    public boolean contains(long pos) {
        return nodes.containsKey(pos);
    }

    /** Adds a node (a placed node block). Replaces one already there, keeping its links. */
    public Node put(long pos, Kind kind, int rotation) {
        Node old = nodes.get(pos);
        Node n = new Node(kind, rotation);
        if (old != null) {
            System.arraycopy(old.ext, 0, n.ext, 0, n.ext.length);
            n.links.addAll(old.links);
        }
        nodes.put(pos, n);
        version++;
        return n;
    }

    /** Loads a saved node (no version bump); its links may point to nodes loaded later, see {@link #pruneLinks()}. */
    public void restore(long pos, Kind kind, int rotation, float[] ext, long[] links) {
        Node n = new Node(kind, rotation);
        if (ext != null && ext.length == n.ext.length) {
            for (int i = 0; i < ext.length; i++) {
                n.ext[i] = Math.max(NodeGeometry.MIN_EXTENT, Math.min(NodeGeometry.MAX_EXTENT, ext[i]));
            }
        }
        for (long l : links) n.links.add(l);
        nodes.put(pos, n);
    }

    /** After loading: drops links to nodes that are gone, and makes every link two-way. */
    public void pruneLinks() {
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            e.getValue().links.removeIf(l -> !nodes.containsKey(l) || l.equals(e.getKey()));
        }
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            for (long l : new ArrayList<>(e.getValue().links)) nodes.get(l).links.add(e.getKey());
        }
    }

    /** Moves one side of a node's box by {@code delta} blocks (clamped); returns the new extent, or -1 if there is no such node. */
    public float resize(long pos, int side, float delta) {
        Node n = nodes.get(pos);
        if (n == null || side < 0 || side >= n.ext.length) return -1;
        n.ext[side] = Math.max(NodeGeometry.MIN_EXTENT, Math.min(NodeGeometry.MAX_EXTENT, n.ext[side] + delta));
        version++;
        return n.ext[side];
    }

    /** Removes a node (a broken node block) and every link to it. */
    public void remove(long pos) {
        Node n = nodes.remove(pos);
        if (n == null) return;
        for (long other : n.links) {
            Node o = nodes.get(other);
            if (o != null) o.links.remove(pos);
        }
        version++;
    }

    /** Links two nodes, or unlinks them if they already are. A track may not hold two start nodes. */
    public LinkResult toggleLink(long a, long b) {
        if (a == b) return LinkResult.SAME_NODE;
        Node na = nodes.get(a);
        Node nb = nodes.get(b);
        if (na == null || nb == null) return LinkResult.MISSING;
        if (na.links.contains(b)) {
            na.links.remove(b);
            nb.links.remove(a);
            version++;
            return LinkResult.UNLINKED;
        }
        if (countStarts(component(a)) > 0 && countStarts(component(b)) > 0 && !component(a).contains(b)) {
            return LinkResult.TWO_STARTS;
        }
        na.links.add(b);
        nb.links.add(a);
        version++;
        return LinkResult.LINKED;
    }

    /** Whether the node at {@code pos} may become {@code kind}: a track may only have one start, so a checkpoint cannot turn into a second one. */
    public boolean canBecome(long pos, Kind kind) {
        if (kind != Kind.START) return true;
        for (long p : component(pos)) {
            if (p != pos && nodes.get(p).kind == Kind.START) return false;
        }
        return true;
    }

    /** Every node reachable from {@code pos} through links (including it). */
    public Set<Long> component(long pos) {
        Set<Long> seen = new LinkedHashSet<>();
        if (!nodes.containsKey(pos)) return seen;
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty()) {
            Node n = nodes.get(queue.poll());
            if (n == null) continue;
            for (long l : n.links) {
                if (seen.add(l)) queue.add(l);
            }
        }
        return seen;
    }

    private int countStarts(Set<Long> comp) {
        int c = 0;
        for (long p : comp) {
            Node n = nodes.get(p);
            if (n != null && n.kind == Kind.START) c++;
        }
        return c;
    }

    /** The start node of the track {@code pos} belongs to, or null if it has none. */
    public Long startOf(long pos) {
        for (long p : component(pos)) {
            if (nodes.get(p).kind == Kind.START) return p;
        }
        return null;
    }

    /** True if the start node lies on a loop: some link of it leads back to it without using that same link. */
    public boolean loopClosed(long start) {
        Node s = nodes.get(start);
        if (s == null || s.links.size() < 2) return false;
        for (long first : s.links) {
            // can we get from `first` back to `start` without crossing the link start-first?
            Set<Long> seen = new HashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(first);
            seen.add(first);
            while (!queue.isEmpty()) {
                long cur = queue.poll();
                Node n = nodes.get(cur);
                if (n == null) continue;
                for (long l : n.links) {
                    if (cur == first && l == start) continue;       // not back along the same link
                    if (l == start) return true;
                    if (seen.add(l)) queue.add(l);
                }
            }
        }
        return false;
    }

    public Summary summary(long pos) {
        Set<Long> comp = component(pos);
        int starts = 0;
        int checkpoints = 0;
        Long start = null;
        for (long p : comp) {
            if (nodes.get(p).kind == Kind.START) {
                starts++;
                start = p;
            } else {
                checkpoints++;
            }
        }
        return new Summary(comp.size(), starts, checkpoints, start != null && loopClosed(start));
    }

    /** The nodes of every track, for listing: each connected group once, as a list. */
    public List<Set<Long>> components() {
        Set<Long> done = new HashSet<>();
        List<Set<Long>> out = new ArrayList<>();
        for (long p : nodes.keySet()) {
            if (done.contains(p)) continue;
            Set<Long> comp = component(p);
            done.addAll(comp);
            out.add(comp);
        }
        return out;
    }
}
