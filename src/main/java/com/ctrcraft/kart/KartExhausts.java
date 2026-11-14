package com.ctrcraft.kart;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ctrcraft.CtrCraftMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The open ends of the kart's exhaust pipes: {@code assets/ctrcraft/kart/kart_exhausts.json}, written by tools/make_kart_ctr.py from the cubes named {@code exhaust_*} of
 * blockbench/kart/kart_ctr.bbmodel: each pipe's tip in blocks from the kart's origin in the model's frame (x to its right, y up, z towards the back) and the way it points. The exhaust
 * smoke and the turbo flame come from there. Read again whenever the resources reload.
 */
public final class KartExhausts {
    private KartExhausts() {}

    public record Pipe(float x, float y, float z, float dx, float dy, float dz) {}

    private static volatile List<Pipe> pipes = List.of(new Pipe(-0.31F, 0.55F, 0.33F, 0.0F, 0.7071F, 0.7071F), new Pipe(0.31F, 0.55F, 0.33F, 0.0F, 0.7071F, 0.7071F));

    public static List<Pipe> get() {
        return pipes;
    }

    /** A reload listener's body. */
    public static void load(ResourceManager manager) {
        Optional<Resource> res = manager.getResource(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart/kart_exhausts.json"));
        if (res.isEmpty()) return;
        try (Reader in = res.get().openAsReader()) {
            List<Pipe> list = new ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("pipes")) {
                JsonObject o = e.getAsJsonObject();
                JsonArray t = o.getAsJsonArray("tip");
                JsonArray d = o.getAsJsonArray("dir");
                list.add(new Pipe(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat(), d.get(0).getAsFloat(), d.get(1).getAsFloat(), d.get(2).getAsFloat()));
            }
            if (!list.isEmpty()) pipes = List.copyOf(list);
        } catch (Exception ex) {
            CtrCraftMod.LOGGER.warn("Could not read the kart's exhausts: {}", ex.toString());
        }
    }
}
