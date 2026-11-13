package com.ctrcraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CtrCraftMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CtrCraftMod.MODID))
                    .icon(() -> new ItemStack(ModItems.KART.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.KART.get());
                        output.accept(ModBlocks.BOOST_PAD_ITEM.get());
                        output.accept(ModBlocks.BOOST_BLOCK_ITEM.get());
                        output.accept(ModBlocks.BOOST_SLAB_ITEM.get());
                        output.accept(ModBlocks.BOOST_STAIRS_ITEM.get());
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModItems.TRACK_TOOL.get());
                        output.accept(ModBlocks.START_NODE_ITEM.get());
                        output.accept(ModBlocks.CHECKPOINT_NODE_ITEM.get());
                        output.accept(ModBlocks.FRUIT_SPOT_ITEM.get());
                        output.accept(ModBlocks.FRUIT_CRATE_SPOT_ITEM.get());
                        output.accept(ModBlocks.POWERUP_CRATE_SPOT_ITEM.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
