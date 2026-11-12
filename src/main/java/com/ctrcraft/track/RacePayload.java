package com.ctrcraft.track;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, to every racer: where the race is and how this racer is doing. Sent when something changes and every few ticks while
 * racing. {@code placeSeq} changes when the server has put the racer on the grid ({@code gx..gyaw}); the client driver then places its sim.
 */
public record RacePayload(int phase, int ticksLeft, int lights, int laps, int lap, int place, int field, int timeMs, int lastLapMs,
                          int bestLapMs, boolean hold, int placeSeq, double gx, double gy, double gz, float gyaw, int finishPlace, int karts)
        implements CustomPacketPayload {
    public static final int OFF = 0;
    public static final int GATHER = 1;
    public static final int LIGHTS = 2;
    public static final int RACING = 3;
    public static final int DONE = 4;

    public static final Type<RacePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "race"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RacePayload> STREAM_CODEC = StreamCodec.of(RacePayload::write, RacePayload::read);

    public static RacePayload off() {
        return new RacePayload(OFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0);
    }

    private static void write(RegistryFriendlyByteBuf b, RacePayload p) {
        b.writeVarInt(p.phase);
        b.writeVarInt(p.ticksLeft);
        b.writeVarInt(p.lights);
        b.writeVarInt(p.laps);
        b.writeVarInt(p.lap);
        b.writeVarInt(p.place);
        b.writeVarInt(p.field);
        b.writeVarInt(p.timeMs);
        b.writeVarInt(p.lastLapMs);
        b.writeVarInt(p.bestLapMs);
        b.writeBoolean(p.hold);
        b.writeVarInt(p.placeSeq);
        b.writeDouble(p.gx);
        b.writeDouble(p.gy);
        b.writeDouble(p.gz);
        b.writeFloat(p.gyaw);
        b.writeVarInt(p.finishPlace);
        b.writeVarInt(p.karts);
    }

    private static RacePayload read(RegistryFriendlyByteBuf b) {
        return new RacePayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readFloat(), b.readVarInt(), b.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RaceClient.update(payload));
    }
}
