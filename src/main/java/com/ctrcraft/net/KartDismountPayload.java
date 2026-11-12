package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Sent by the driver's client the moment sneak is pressed, with the kart's exact pose. Vanilla dismounts on the server with
 * the kart's position as of the previous move packet, which at kart speeds is a block or more behind (and the last move
 * packet is then ignored because the player has already left). This puts the kart where the driver really is, then dismounts.
 */
public record KartDismountPayload(int entityId, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<KartDismountPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart_dismount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KartDismountPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KartDismountPayload::entityId,
            ByteBufCodecs.DOUBLE, KartDismountPayload::x,
            ByteBufCodecs.DOUBLE, KartDismountPayload::y,
            ByteBufCodecs.DOUBLE, KartDismountPayload::z,
            KartDismountPayload::new);

    /** The furthest the client may move the kart with this packet, blocks: more than a few ticks of top speed is refused. */
    private static final double MAX_CORRECTION = 12.0;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KartDismountPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof KartEntity kart) || kart.getControllingPassenger() != player) {
                return;
            }
            double dx = payload.x() - kart.getX();
            double dy = payload.y() - kart.getY();
            double dz = payload.z() - kart.getZ();
            double off = Math.sqrt(dx * dx + dy * dy + dz * dz);
            CtrCraftMod.LOGGER.info("Kart dismount: the server's kart was {} blocks from the driver's", String.format("%.2f", off));
            if (off <= MAX_CORRECTION && Double.isFinite(off)) {
                kart.absMoveTo(payload.x(), payload.y(), payload.z(), kart.getYRot(), kart.getXRot());
            }
            player.stopRiding();
        });
    }
}
