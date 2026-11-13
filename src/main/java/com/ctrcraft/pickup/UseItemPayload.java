package com.ctrcraft.pickup;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the use-item key was pressed; {@code forward} is whether the forward key was held (the original's d-pad up: a flask is thrown, not dropped). */
public record UseItemPayload(boolean forward) implements CustomPacketPayload {
    public static final Type<UseItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "use_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UseItemPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.BOOL, UseItemPayload::forward, UseItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UseItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer p) ItemManager.use(p, payload.forward());
        });
    }
}
