package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.List;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The track nodes of the player's dimension, sent to a player who is holding a track item so the client can draw the ghosts. Resent
 * when the graph changes. {@code version} is the server's graph version, for the sender's bookkeeping.
 */
public record TrackSnapshotPayload(int version, List<NodeView> nodes) implements CustomPacketPayload {
    public static final Type<TrackSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "track_snapshot"));

    /** One node as the client sees it. */
    public record NodeView(long pos, TrackGraph.Kind kind, int rotation, float[] ext, long[] links) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(TrackSnapshotPayload::write, TrackSnapshotPayload::read);

    /** More than this many nodes are not sent (a sanity limit, a track has a few dozen). */
    private static final int MAX_NODES = 4000;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static TrackSnapshotPayload of(TrackGraph graph) {
        List<NodeView> out = new ArrayList<>();
        for (var e : graph.nodes().entrySet()) {
            if (out.size() >= MAX_NODES) break;
            TrackGraph.Node n = e.getValue();
            out.add(new NodeView(e.getKey(), n.kind, n.rotation, n.ext.clone(), n.links().stream().mapToLong(Long::longValue).toArray()));
        }
        return new TrackSnapshotPayload(graph.version(), out);
    }

    private static void write(RegistryFriendlyByteBuf b, TrackSnapshotPayload p) {
        b.writeVarInt(p.version);
        b.writeVarInt(p.nodes.size());
        for (NodeView n : p.nodes) {
            b.writeLong(n.pos());
            b.writeByte(n.kind().ordinal());
            b.writeByte(n.rotation());
            for (float f : n.ext()) b.writeFloat(f);
            b.writeVarInt(n.links().length);
            for (long l : n.links()) b.writeLong(l);
        }
    }

    private static TrackSnapshotPayload read(RegistryFriendlyByteBuf b) {
        int version = b.readVarInt();
        int count = Math.min(b.readVarInt(), MAX_NODES);
        List<NodeView> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long pos = b.readLong();
            int kind = Math.max(0, Math.min(TrackGraph.Kind.values().length - 1, b.readByte()));
            int rotation = b.readByte() & 15;
            float[] ext = new float[6];
            for (int k = 0; k < 6; k++) ext[k] = b.readFloat();
            int links = Math.min(b.readVarInt(), 64);
            long[] l = new long[links];
            for (int k = 0; k < links; k++) l[k] = b.readLong();
            nodes.add(new NodeView(pos, TrackGraph.Kind.values()[kind], rotation, ext, l));
        }
        return new TrackSnapshotPayload(version, nodes);
    }

    public static void handle(TrackSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrackClient.update(payload));
    }
}
