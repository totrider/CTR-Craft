package com.ctrcraft.kart;

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
 * The steering wheel's pivot and axis: {@code assets/ctrcraft/kart/kart_steering.json}, written by tools/make_kart_ctr.py from the steering bar of blockbench/kart/kart_ctr.bbmodel. The
 * wheel's parts (steering_1 and up) are the model {@code item/kart_steering}; the renderer turns them about this axis through this pivot (blocks, in the kart model's own frame)
 * by the steering. The rod (steering_0) is part of the kart's own model and does not turn. Null until read (nothing turns then). Read again whenever the resources reload.
 */
public final class KartSteering {
    private KartSteering() {}

    public record Axis(float px, float py, float pz, float ax, float ay, float az, float[] gripLeft, float[] gripRight) {}

    private static volatile Axis axis;

    public static Axis get() {
        return axis;
    }

    private static float[] grip(JsonObject o, String key) {
        JsonArray g = o.getAsJsonArray(key);
        return new float[] { g.get(0).getAsFloat(), g.get(1).getAsFloat(), g.get(2).getAsFloat() };
    }

    /** A reload listener's body. */
    public static void load(ResourceManager manager) {
        Optional<Resource> res = manager.getResource(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart/kart_steering.json"));
        if (res.isEmpty()) {
            axis = null;
            return;
        }
        try (Reader in = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            JsonArray p = o.getAsJsonArray("pivot");
            JsonArray a = o.getAsJsonArray("axis");
            axis = new Axis(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(), a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), grip(o, "grip_left"), grip(o, "grip_right"));
        } catch (Exception ex) {
            CtrCraftMod.LOGGER.warn("Could not read the kart's steering axis: {}", ex.toString());
            axis = null;
        }
    }
}
