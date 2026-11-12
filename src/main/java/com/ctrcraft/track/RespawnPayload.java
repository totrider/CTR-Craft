package com.ctrcraft.track;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the respawn key was pressed. The server only does something during a race (see {@link RaceManager#respawn}). */
public record RespawnPayload() implements CustomPacketPayload {
    public static final Type<RespawnPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "respawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RespawnPayload> STREAM_CODEC = StreamCodec.unit(new RespawnPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RespawnPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) RaceManager.respawn(player);
        });
    }
}
