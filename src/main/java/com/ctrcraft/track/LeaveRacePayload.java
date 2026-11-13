package com.ctrcraft.track;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the leave-race key was held long enough. In a race sneaking does not get you out of the kart; this does (see {@link RaceManager#forfeit}). */
public record LeaveRacePayload() implements CustomPacketPayload {
    public static final Type<LeaveRacePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "leave_race"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeaveRacePayload> STREAM_CODEC = StreamCodec.unit(new LeaveRacePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LeaveRacePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) RaceManager.forfeit(player);
        });
    }
}
