package com.ctrcraft.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ctrcraft.CtrConfig;
import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.ContactRules;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.physics.KartBump;
import com.ctrcraft.physics.KartClass;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The driver's client says its kart has hit something other than another kart (a mob, a boat, a player on foot): the entity's id, how
 * hard (the bump's impact, velocity units) and the direction it was hit in (kart to entity, flat). The server checks the driver is in that
 * kart, the entity is close, and the rate, then throws the entity and, if damage is on, hurts it. The kart's own bounce is the client's.
 */
public record KartHitPayload(int kartId, int targetId, int impact, float dirX, float dirZ) implements CustomPacketPayload {
    public static final Type<KartHitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart_hit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KartHitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KartHitPayload::kartId,
            ByteBufCodecs.VAR_INT, KartHitPayload::targetId,
            ByteBufCodecs.VAR_INT, KartHitPayload::impact,
            ByteBufCodecs.FLOAT, KartHitPayload::dirX,
            ByteBufCodecs.FLOAT, KartHitPayload::dirZ,
            KartHitPayload::new);

    /** The hardest a report may claim: well over a kart's top speed head-on into something standing still. */
    private static final int MAX_IMPACT = 40000;
    /** Last hit by (driver, target) pair, game tick. */
    private static final Map<String, Integer> LAST_HIT = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clear() {
        LAST_HIT.clear();
    }

    public static void handle(KartHitPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer driver) || !CtrConfig.ENTITY_CONTACT.get()) return;
            if (!(driver.getVehicle() instanceof KartEntity kart) || kart.getId() != p.kartId() || kart.getControllingPassenger() != driver) return;
            Entity target = driver.level().getEntity(p.targetId());
            if (target == null || target.isRemoved() || target == driver || target instanceof KartEntity || target.isSpectator()) return;
            if (kart.hasPassenger(target) || target.getVehicle() != null) return;
            if (p.impact() <= 0 || p.impact() > MAX_IMPACT || !Float.isFinite(p.dirX()) || !Float.isFinite(p.dirZ())) return;
            if (kart.distanceTo(target) > ContactRules.MAX_REACH_BLOCKS) return;
            int now = driver.server.getTickCount();
            Integer last = LAST_HIT.put(driver.getUUID() + ":" + target.getUUID(), now);
            if (last != null && now - last < ContactRules.HIT_COOLDOWN_TICKS) return;
            if (LAST_HIT.size() > 2048) LAST_HIT.values().removeIf(t -> now - t > 200);

            double len = Math.sqrt(p.dirX() * p.dirX() + p.dirZ() * p.dirZ());
            if (len < 1e-4) return;
            double bps = ContactRules.blocksPerSecond(p.impact());
            int entityWeight = ContactRules.weight(target.getBbWidth(), target.getBbHeight());
            double k = ContactRules.knockback(bps, KartBump.weight(KartClass.BALANCED), entityWeight);
            Vec3 push = new Vec3(p.dirX() / len * k, Math.min(0.4, 0.1 + k * 0.15), p.dirZ() / len * k);
            target.setDeltaMovement(target.getDeltaMovement().add(push));
            target.hasImpulse = true;
            if (target instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));

            if (CtrConfig.ENTITY_DAMAGE.get() && target instanceof LivingEntity living) {
                float damage = ContactRules.damage(bps, CtrConfig.ENTITY_DAMAGE_SCALE.get());
                if (damage > 0 && canHurt(driver, living)) {
                    DamageSource source = driver.damageSources().playerAttack(driver);
                    living.hurt(source, damage);
                }
            }
        });
    }

    /** Pets of the driver are spared; players only by the server's player-versus-player rules and the config. */
    private static boolean canHurt(ServerPlayer driver, LivingEntity target) {
        if (target instanceof TamableAnimal pet && pet.isOwnedBy(driver)) return false;
        if (target instanceof Player other) {
            if (other.getUUID().equals(driver.getUUID())) return false;
            return CtrConfig.ENTITY_DAMAGE_PLAYERS.get() && driver.server.isPvpAllowed() && driver.canHarmPlayer(other);
        }
        return true;
    }
}
