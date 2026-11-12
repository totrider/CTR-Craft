package com.ctrcraft.track;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: open the track menu of the Start node at {@code start}, with what it should say about the track. */
public record TrackMenuPayload(long start, boolean driving, boolean hasMain, int nodes, int branches, int uncovered, boolean valid, int laps, boolean racing)
        implements CustomPacketPayload {
    public static final Type<TrackMenuPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "track_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackMenuPayload> STREAM_CODEC = StreamCodec.of(TrackMenuPayload::write, TrackMenuPayload::read);

    private static void write(RegistryFriendlyByteBuf b, TrackMenuPayload p) {
        b.writeLong(p.start);
        b.writeBoolean(p.driving);
        b.writeBoolean(p.hasMain);
        b.writeVarInt(p.nodes);
        b.writeVarInt(p.branches);
        b.writeVarInt(p.uncovered);
        b.writeBoolean(p.valid);
        b.writeVarInt(p.laps);
        b.writeBoolean(p.racing);
    }

    private static TrackMenuPayload read(RegistryFriendlyByteBuf b) {
        return new TrackMenuPayload(b.readLong(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrackMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.ctrcraft.client.TrackMenuScreen.open(payload));
    }
}
