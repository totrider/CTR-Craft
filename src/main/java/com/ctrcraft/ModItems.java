package com.ctrcraft;

import com.ctrcraft.kart.KartItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CtrCraftMod.MODID);

    /** Places a kart. Milestone 1: creative-tab only (no recipe yet; survival comes in milestone 3). */
    public static final DeferredItem<KartItem> KART = ITEMS.register("kart", () -> new KartItem(new Item.Properties().stacksTo(1)));

    /** Links track nodes (see docs/DESIGN_TRACKS.md). */
    public static final DeferredItem<com.ctrcraft.track.TrackToolItem> TRACK_TOOL = ITEMS.register("track_tool",
            () -> new com.ctrcraft.track.TrackToolItem(new Item.Properties().stacksTo(1)));

    /** Changes a kart's settings (right-click the kart): whether it can start races, later its tuning profile. */
    public static final DeferredItem<com.ctrcraft.kart.WrenchItem> WRENCH = ITEMS.register("wrench",
            () -> new com.ctrcraft.kart.WrenchItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
