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

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
