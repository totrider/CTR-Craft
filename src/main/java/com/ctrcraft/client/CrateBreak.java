package com.ctrcraft.client;

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
 * The pieces of a breaking crate (the original's STATIC_CRATE_EXPLOSION, an animation of triangles): {@code assets/ctrcraft/crate_break/crate_break.json} has the {@code frames},
 * the picture sheet's {@code texture} and for each triangle its {@code uv} (three corners) and {@code pos}, the three corners in every frame, in blocks, the crate's middle at
 * (0, 0.5, 0). The jar has a stand-in (tools/make_crate_break.py); the original's is in the local resource pack. Read again whenever the resources reload.
 */
public final class CrateBreak {
    private CrateBreak() {}

    public record Triangle(float[] uv, float[][] pos) {}

    public record Data(ResourceLocation texture, int frames, List<Triangle> triangles) {}

    private static Data data;

    public static Data get() {
        return data;
    }

    /** A reload listener's body. */
    public static void load(ResourceManager manager) {
        data = read(manager);
        CtrCraftMod.LOGGER.info("Crate break: {}", data == null ? "none" : data.triangles().size() + " pieces of " + data.frames() + " frames (" + data.texture() + ")");
    }

    private static Data read(ResourceManager manager) {
        Optional<Resource> res = manager.getResource(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "crate_break/crate_break.json"));
        if (res.isEmpty()) return null;
        try (Reader in = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            int frames = o.get("frames").getAsInt();
            List<Triangle> tris = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("triangles")) {
                JsonObject t = e.getAsJsonObject();
                JsonArray uv = t.getAsJsonArray("uv");
                JsonArray ps = t.getAsJsonArray("pos");
                float[] u = new float[6];
                for (int i = 0; i < 6; i++) u[i] = uv.get(i).getAsFloat();
                float[][] pos = new float[Math.min(frames, ps.size())][9];
                for (int f = 0; f < pos.length; f++) {
                    JsonArray row = ps.get(f).getAsJsonArray();
                    for (int i = 0; i < 9; i++) pos[f][i] = row.get(i).getAsFloat();
                }
                tris.add(new Triangle(u, pos));
            }
            return new Data(ResourceLocation.parse(o.get("texture").getAsString()), frames, tris);
        } catch (Exception ex) {
            CtrCraftMod.LOGGER.warn("Could not read the crate break: {}", ex.toString());
            return null;
        }
    }
}
