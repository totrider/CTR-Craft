package com.ctrcraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags that say what a block is to a kart (data-driven: a data pack can add to or replace them, e.g. to make a block
 * count as ice or as a turbo pad). A block in none of the surface tags is asphalt: full speed. Files:
 * data/ctrcraft/tags/block/surface/*.json and data/ctrcraft/tags/block/turbo_pad.json.
 */
public final class ModTags {
    private ModTags() {}

    public static final TagKey<Block> SURFACE_DIRT = tag("surface/dirt");
    public static final TagKey<Block> SURFACE_SLOW_DIRT = tag("surface/slow_dirt");
    public static final TagKey<Block> SURFACE_WATER = tag("surface/water");
    public static final TagKey<Block> SURFACE_ICE = tag("surface/ice");
    public static final TagKey<Block> SURFACE_ICY_ROAD = tag("surface/icy_road");
    public static final TagKey<Block> SURFACE_SNOW = tag("surface/snow");
    public static final TagKey<Block> SURFACE_MUD = tag("surface/mud");
    public static final TagKey<Block> SURFACE_SIDESLIP = tag("surface/sideslip");
    public static final TagKey<Block> TURBO_PAD = tag("turbo_pad");

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, path));
    }
}
