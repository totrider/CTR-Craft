package com.ctrcraft.track;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The tracks of one dimension, saved with the world. The node blocks are only markers: this is where nodes and their links live
 * (see docs/DESIGN_TRACKS.md). Breaking a node block removes it from here.
 */
public class TrackData extends SavedData {
    private static final String NAME = "ctrcraft_tracks";

    private TrackGraph graph = new TrackGraph();
    private int startsVersion = -1;
    private java.util.List<Long> startsCache = java.util.List.of();

    /** The Start nodes of this dimension (cached until the graph changes): the race areas to check a moving kart against. */
    public java.util.List<Long> starts() {
        if (graph.version() != startsVersion) {
            startsVersion = graph.version();
            java.util.List<Long> out = new java.util.ArrayList<>();
            for (Map.Entry<Long, TrackGraph.Node> e : graph.nodes().entrySet()) {
                if (e.getValue().kind == TrackGraph.Kind.START) out.add(e.getKey());
            }
            startsCache = java.util.List.copyOf(out);
        }
        return startsCache;
    }

    /** What test drives recorded, by the start node of the track. */
    private final Map<Long, TrackRoutes> routes = new HashMap<>();

    public static TrackData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TrackData::new, TrackData::load, null), NAME);
    }

    public TrackGraph graph() {
        return graph;
    }

    /** The recorded routes of the track starting at {@code start}, checked against the graph (empty if there are none or they no longer fit). */
    public TrackRoutes routes(long start) {
        TrackRoutes r = routes.computeIfAbsent(start, k -> new TrackRoutes());
        r.verify(graph, start);
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, TrackGraph.Node> e : graph.nodes().entrySet()) {
            TrackGraph.Node n = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getKey());
            t.putByte("kind", (byte) n.kind.ordinal());
            t.putByte("rot", (byte) n.rotation);
            ListTag ext = new ListTag();
            for (float f : n.ext) ext.add(net.minecraft.nbt.FloatTag.valueOf(f));
            t.put("ext", ext);
            long[] links = n.links().stream().mapToLong(Long::longValue).toArray();
            t.putLongArray("links", links);
            list.add(t);
        }
        tag.put("nodes", list);
        ListTag rl = new ListTag();
        for (Map.Entry<Long, TrackRoutes> e : routes.entrySet()) {
            TrackRoutes r = e.getValue();
            if ((!r.hasMain() && r.laps() == 3) || !graph.contains(e.getKey())) continue;
            CompoundTag t = new CompoundTag();
            t.putLong("start", e.getKey());
            t.putInt("laps", r.laps());
            t.putLongArray("main", r.main().stream().mapToLong(Long::longValue).toArray());
            ListTag bl = new ListTag();
            for (TrackRoutes.Branch b : r.branches()) {
                CompoundTag bt = new CompoundTag();
                bt.putLong("from", b.from());
                bt.putLong("to", b.to());
                bt.putLongArray("nodes", b.nodes());
                bl.add(bt);
            }
            t.put("branches", bl);
            rl.add(t);
        }
        tag.put("routes", rl);
        return tag;
    }

    private static TrackData load(CompoundTag tag, HolderLookup.Provider registries) {
        TrackData data = new TrackData();
        ListTag list = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int kind = Math.max(0, Math.min(TrackGraph.Kind.values().length - 1, t.getByte("kind")));
            ListTag extTag = t.getList("ext", Tag.TAG_FLOAT);
            float[] ext = null;
            if (extTag.size() == 6) {
                ext = new float[6];
                for (int k = 0; k < 6; k++) ext[k] = extTag.getFloat(k);
            }
            data.graph.restore(t.getLong("pos"), TrackGraph.Kind.values()[kind], t.getByte("rot"), ext, t.getLongArray("links"));
        }
        data.graph.pruneLinks();
        ListTag rl = tag.getList("routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            CompoundTag t = rl.getCompound(i);
            TrackRoutes r = new TrackRoutes();
            if (t.contains("laps")) r.setLaps(t.getInt("laps"));
            if (t.getLongArray("main").length > 0) r.setMain(java.util.Arrays.stream(t.getLongArray("main")).boxed().toList());
            ListTag bl = t.getList("branches", Tag.TAG_COMPOUND);
            for (int k = 0; k < bl.size(); k++) {
                CompoundTag bt = bl.getCompound(k);
                r.addBranch(new TrackRoutes.Branch(bt.getLong("from"), bt.getLong("to"), bt.getLongArray("nodes")));
            }
            data.routes.put(t.getLong("start"), r);
        }
        return data;
    }
}
