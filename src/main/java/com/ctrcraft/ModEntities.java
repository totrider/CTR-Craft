package com.ctrcraft;

import com.ctrcraft.kart.KartEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CtrCraftMod.MODID);

    /**
     * The kart. The hitbox is smaller than the physics sphere (radius 0.5 block) on purpose: the server validates
     * a driver's vehicle movement with vanilla box collision, and a box that fits inside the sphere never
     * collides where the sphere did not (see docs/ROADMAP.md, networking).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<KartEntity>> KART = ENTITIES.register("kart", () ->
            EntityType.Builder.<KartEntity>of(KartEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.5F).passengerAttachments(0.2F).clientTrackingRange(16).updateInterval(1)
                    .build("kart"));

    /** A fruit or crate waiting at a pickup spot (never saved; the spot makes it). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.ctrcraft.pickup.PickupEntity>> PICKUP = ENTITIES.register("pickup", () ->
            EntityType.Builder.<com.ctrcraft.pickup.PickupEntity>of(com.ctrcraft.pickup.PickupEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(20).noSave().build("pickup"));

    /** N. Brio's Beaker (never saved). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.ctrcraft.pickup.PotionEntity>> POTION = ENTITIES.register("potion", () ->
            EntityType.Builder.<com.ctrcraft.pickup.PotionEntity>of(com.ctrcraft.pickup.PotionEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.6F).clientTrackingRange(10).updateInterval(1).noSave().build("potion"));

    /** The TNT / Nitro crate of the TNT power-up (never saved). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.ctrcraft.pickup.TntEntity>> TNT = ENTITIES.register("tnt", () ->
            EntityType.Builder.<com.ctrcraft.pickup.TntEntity>of(com.ctrcraft.pickup.TntEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F).clientTrackingRange(10).updateInterval(2).noSave().build("tnt"));

    /** The Bomb power-up's rolling bomb (never saved). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.ctrcraft.pickup.BombEntity>> BOMB = ENTITIES.register("bomb", () ->
            EntityType.Builder.<com.ctrcraft.pickup.BombEntity>of(com.ctrcraft.pickup.BombEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(10).updateInterval(1).noSave().build("bomb"));

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
