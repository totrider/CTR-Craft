package com.ctrcraft.pickup;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the driver's kart started a jump while a TNT crate was on its head ({@link TntEntity#hop}, which counts them as RB_TNT_ThTick_SitOnHead does). */
public record TntShakePayload() implements CustomPacketPayload {
    public static final Type<TntShakePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "tnt_shake"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TntShakePayload> STREAM_CODEC = StreamCodec.unit(new TntShakePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TntShakePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer p)) return;
            for (TntEntity tnt : p.serverLevel().getEntitiesOfClass(TntEntity.class, p.getBoundingBox().inflate(3.0))) tnt.hop(p);
        });
    }
}
