package com.ctrcraft.client;

import java.io.Reader;
import java.util.Optional;

import com.ctrcraft.CtrCraftMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Where the bomb's spark is: {@code assets/ctrcraft/bomb/bomb_fuse.json} has the {@code tip} of the fuse in blocks from the ball's middle (the model's own frame, the ball rolls about
 * the x axis through it), the {@code spark_size} (the billboard's width) and {@code ground}, the ball's radius (how far its middle is above the floor). The jar has a stand-in
 * (tools/make_bomb_model.py); the original's, from the disc, is in the local resource pack. Read again whenever the resources reload.
 */
public final class BombFuse {
    private BombFuse() {}

    public record Data(float tipX, float tipY, float tipZ, float sparkSize, float ground) {}

    private static Data data = new Data(-0.55F, 0.0F, 0.31F, 0.8F, 0.4F);

    public static Data get() {
        return data;
    }

    /** A reload listener's body. */
    public static void load(ResourceManager manager) {
        Optional<Resource> res = manager.getResource(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "bomb/bomb_fuse.json"));
        if (res.isEmpty()) return;
        try (Reader in = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            JsonArray tip = o.getAsJsonArray("tip");
            data = new Data(tip.get(0).getAsFloat(), tip.get(1).getAsFloat(), tip.get(2).getAsFloat(), o.get("spark_size").getAsFloat(), o.get("ground").getAsFloat());
            CtrCraftMod.LOGGER.info("Bomb fuse: tip ({}, {}, {}), spark {} blocks, ball radius {}", data.tipX(), data.tipY(), data.tipZ(), data.sparkSize(), data.ground());
        } catch (Exception ex) {
            CtrCraftMod.LOGGER.warn("Could not read the bomb fuse: {}", ex.toString());
        }
    }
}
