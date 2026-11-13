package com.ctrcraft;

import com.ctrcraft.pickup.PickupSpotBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CtrCraftMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PickupSpotBlockEntity>> PICKUP_SPOT = BLOCK_ENTITIES.register("pickup_spot",
            () -> BlockEntityType.Builder.of(PickupSpotBlockEntity::new, ModBlocks.FRUIT_SPOT.get(), ModBlocks.FRUIT_CRATE_SPOT.get(),
                    ModBlocks.POWERUP_CRATE_SPOT.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
