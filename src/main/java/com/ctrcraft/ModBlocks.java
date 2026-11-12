package com.ctrcraft;

import java.util.function.Supplier;

import com.ctrcraft.kart.BoostBlock;
import com.ctrcraft.kart.BoostPadBlock;
import com.ctrcraft.kart.BoostSlabBlock;
import com.ctrcraft.kart.BoostStairsBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The boost blocks: all are in the {@code ctrcraft:turbo_pad} tag, so a kart over any of them gets CTR's pad boost. Each has a
 * direction (an arrow on top); the plate has no collision.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CtrCraftMod.MODID);

    private static BlockBehaviour.Properties solid() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.5F, 6.0F).sound(SoundType.METAL);
    }

    /** A 1 px plate on the floor, no collision. */
    public static final DeferredBlock<BoostPadBlock> BOOST_PAD = BLOCKS.register("boost_pad", () -> new BoostPadBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.METAL)
                    .noCollission().noOcclusion()));
    public static final DeferredBlock<BoostBlock> BOOST_BLOCK = BLOCKS.register("boost_block", () -> new BoostBlock(solid()));
    public static final DeferredBlock<BoostSlabBlock> BOOST_SLAB = BLOCKS.register("boost_slab", () -> new BoostSlabBlock(solid()));
    public static final DeferredBlock<BoostStairsBlock> BOOST_STAIRS = BLOCKS.register("boost_stairs",
            () -> new BoostStairsBlock(BOOST_BLOCK.get().defaultBlockState(), solid()));

    public static final DeferredItem<BlockItem> BOOST_PAD_ITEM = item("boost_pad", BOOST_PAD);
    public static final DeferredItem<BlockItem> BOOST_BLOCK_ITEM = item("boost_block", BOOST_BLOCK);
    public static final DeferredItem<BlockItem> BOOST_SLAB_ITEM = item("boost_slab", BOOST_SLAB);
    public static final DeferredItem<BlockItem> BOOST_STAIRS_ITEM = item("boost_stairs", BOOST_STAIRS);

    /** Track nodes: invisible, no collision; only seen with the Track Tool in hand. */
    public static final DeferredBlock<com.ctrcraft.track.TrackNodeBlock> START_NODE = BLOCKS.register("start_node",
            () -> new com.ctrcraft.track.TrackNodeBlock(nodeProps(), com.ctrcraft.track.TrackGraph.Kind.START));
    public static final DeferredBlock<com.ctrcraft.track.TrackNodeBlock> CHECKPOINT_NODE = BLOCKS.register("checkpoint_node",
            () -> new com.ctrcraft.track.TrackNodeBlock(nodeProps(), com.ctrcraft.track.TrackGraph.Kind.CHECKPOINT));
    public static final DeferredItem<BlockItem> START_NODE_ITEM = ModItems.ITEMS.register("start_node",
            () -> new com.ctrcraft.track.TrackNodeItem(START_NODE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CHECKPOINT_NODE_ITEM = ModItems.ITEMS.register("checkpoint_node",
            () -> new com.ctrcraft.track.TrackNodeItem(CHECKPOINT_NODE.get(), new Item.Properties()));

    private static BlockBehaviour.Properties nodeProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5F).sound(SoundType.METAL)
                .noCollission().noOcclusion();
    }

    private ModBlocks() {}

    private static DeferredItem<BlockItem> item(String name, Supplier<? extends Block> block) {
        return ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
