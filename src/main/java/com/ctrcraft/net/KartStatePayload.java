package com.ctrcraft.net;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.kart.KartFlags;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The driver's client reports its kart once a game tick: where it is, which way it faces, how to draw it (CTR's drawn yaw with
 * the drift angle), the effect flags ({@link KartFlags}) and the engine sound. This replaces vanilla's move-vehicle packet on
 * purpose: vanilla re-runs a box collision of its own over every reported move and rolls the vehicle back when it disagrees,
 * which a sphere-driven kart on blocks triggers now and then ("moved wrongly" in the log, then a snap back and a teleport on
 * dismount). The server here trusts the driver within sane limits, as the physics is client-side by design (docs/ROADMAP.md).
 */
public record KartStatePayload(int entityId, double x, double y, double z, float yRot, int drawnYaw, int flags, int engine, int tilt)
        implements CustomPacketPayload {
    public static final Type<KartStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart_state"));

    // written by hand: StreamCodec.composite takes at most 6 fields
    public static final StreamCodec<RegistryFriendlyByteBuf, KartStatePayload> STREAM_CODEC =
            StreamCodec.of(KartStatePayload::write, KartStatePayload::read);

    private static void write(RegistryFriendlyByteBuf b, KartStatePayload p) {
        b.writeVarInt(p.entityId);
        b.writeDouble(p.x);
        b.writeDouble(p.y);
        b.writeDouble(p.z);
        b.writeFloat(p.yRot);
        b.writeVarInt(p.drawnYaw);
        b.writeVarInt(p.flags);
        b.writeVarInt(p.engine);
        b.writeVarInt(p.tilt);
    }

    private static KartStatePayload read(RegistryFriendlyByteBuf b) {
        return new KartStatePayload(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    /** The furthest one report may move the kart, blocks: top speed with a boost is about 2 blocks a tick. */
    public static final double MAX_STEP = 24.0;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KartStatePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(p.entityId());
            if (!(entity instanceof KartEntity kart) || kart.getControllingPassenger() != player) {
                return;
            }
            if (!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z()) || !Float.isFinite(p.yRot())) {
                return;
            }
            ServerLevel level = player.serverLevel();
            double dx = p.x() - kart.getX();
            double dy = p.y() - kart.getY();
            double dz = p.z() - kart.getZ();
            boolean sane = dx * dx + dy * dy + dz * dz <= MAX_STEP * MAX_STEP
                    && level.hasChunkAt(BlockPos.containing(p.x(), p.y(), p.z()))
                    && level.getWorldBorder().isWithinBounds(p.x(), p.z());
            if (!sane) {
                if (kart.noteRejectedMove() >= 20) {
                    CtrCraftMod.LOGGER.warn("Kart of {} reported impossible moves for a second; dismounting", player.getName().getString());
                    player.stopRiding();
                }
                return;
            }
            kart.clearRejectedMoves();
            double fromX = kart.getX();
            double fromY = kart.getY();
            double fromZ = kart.getZ();
            kart.absMoveTo(p.x(), p.y(), p.z(), p.yRot(), 0.0F);
            kart.setDrawnYawAngle(p.drawnYaw() & 0xFFF);
            kart.setVisualFlags(p.flags() & KartFlags.MASK);
            kart.setEngineData(p.engine() & 0xFFFF);
            kart.setTilt(p.tilt() & 0xFFFF);
            // the rider goes with it, and the chunks around the player follow (vanilla does both after a vehicle move)
            kart.positionRider(player);
            level.getChunkSource().move(player);
            com.ctrcraft.track.TestDriveManager.onKartMoved(player, fromX, fromY, fromZ, p.x(), p.y(), p.z());
            com.ctrcraft.track.RaceManager.onKartMoved(player, fromX, fromY, fromZ, p.x(), p.y(), p.z());
        });
    }
}
