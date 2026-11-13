package com.ctrcraft.pickup;

import java.util.UUID;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.core.UUIDUtil;

/** Server to client: this racer was hurt (a spin or a blast). The race HUD flashes their portrait red for a second (damageColorTimer = 0x1e frames). */
public record HitFlashPayload(UUID racer) implements CustomPacketPayload {
    public static final Type<HitFlashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "hit_flash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HitFlashPayload> STREAM_CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, HitFlashPayload::racer, HitFlashPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HitFlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.ctrcraft.client.RaceHud.flash(payload.racer()));
    }
}
