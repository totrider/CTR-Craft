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
 * The bubbles that rise in N. Brio's Beaker: billboards, because that is how little the beaker's animation is (six of its forty triangles move, the other thirty-four never do).
 * The data is {@code assets/ctrcraft/beaker/beaker_<green|red>_bubbles.json}: {@code frames}, the sprite sheet's {@code texture}, and for each bubble its {@code uv} in the sheet,
 * its {@code width} and {@code height} and the middle of it, {@code centres}, in every frame; all in blocks, x and z from the middle of the flask and y from its base. The
 * jar has a generic version (tools/make_beaker_models.py); the original's, from the disc, is in the local resource pack. Read again whenever the resources reload.
 */
public final class BeakerBubbles {
    private BeakerBubbles() {}

    public record Bubble(float u0, float v0, float u1, float v1, float width, float height, float[][] centres) {}

    public record Data(ResourceLocation texture, int frames, List<Bubble> bubbles) {}

    private static Data green;
    private static Data red;

    public static Data get(boolean redBeaker) {
        return redBeaker ? red : green;
    }

    /** A reload listener's body. */
    public static void load(ResourceManager manager) {
        green = read(manager, "beaker_green_bubbles");
        red = read(manager, "beaker_red_bubbles");
        CtrCraftMod.LOGGER.info("Beaker bubbles: green {}, red {}", describe(green), describe(red));
    }

    private static String describe(Data d) {
        return d == null ? "none" : d.bubbles().size() + " bubbles of " + d.frames() + " frames (" + d.texture() + ")";
    }

    private static Data read(ResourceManager manager, String name) {
        Optional<Resource> res = manager.getResource(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "beaker/" + name + ".json"));
        if (res.isEmpty()) return null;
        try (Reader in = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            int frames = o.get("frames").getAsInt();
            List<Bubble> bubbles = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("bubbles")) {
                JsonObject b = e.getAsJsonObject();
                JsonArray uv = b.getAsJsonArray("uv");
                JsonArray cs = b.getAsJsonArray("centres");
                float[][] centres = new float[cs.size()][3];
                for (int i = 0; i < cs.size(); i++) {
                    JsonArray c = cs.get(i).getAsJsonArray();
                    for (int k = 0; k < 3; k++) centres[i][k] = c.get(k).getAsFloat();
                }
                bubbles.add(new Bubble(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat(), b.get("width").getAsFloat(),
                        b.get("height").getAsFloat(), centres));
            }
            return new Data(ResourceLocation.parse(o.get("texture").getAsString()), Math.min(frames, bubbles.isEmpty() ? frames : bubbles.get(0).centres().length), bubbles);
        } catch (Exception ex) {
            CtrCraftMod.LOGGER.warn("Could not read the beaker bubbles {}: {}", name, ex.toString());
            return null;
        }
    }
}
