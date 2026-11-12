package com.ctrcraft.track;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The client's copy of the track nodes (what the server last sent to a player holding a track item). Plain data, no client classes. */
public final class TrackClient {
    private TrackClient() {}

    private static volatile List<TrackSnapshotPayload.NodeView> nodes = List.of();
    private static volatile Map<Long, TrackSnapshotPayload.NodeView> byPos = Map.of();

    static void update(TrackSnapshotPayload payload) {
        Map<Long, TrackSnapshotPayload.NodeView> map = new java.util.HashMap<>();
        for (TrackSnapshotPayload.NodeView n : payload.nodes()) map.put(n.pos(), n);
        byPos = Collections.unmodifiableMap(map);
        nodes = List.copyOf(payload.nodes());
    }

    public static List<TrackSnapshotPayload.NodeView> nodes() {
        return nodes;
    }

    public static TrackSnapshotPayload.NodeView get(long pos) {
        return byPos.get(pos);
    }

    public static void clear() {
        nodes = List.of();
        byPos = Map.of();
    }
}
