package com.ctrcraft.kart;

import java.io.Reader;
import java.util.Optional;

import com.ctrcraft.CtrCraftMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Where the kart's shadow picture lies: {@code assets/ctrcraft/kart/kart_shadow.json}, written by tools/make_kart_shadow.py with the picture (made from the kart's model seen from
 * above): its corners in blocks from the kart's origin, the front being -z. Null until read; the renderer then falls back to the span of the wheels. Read again whenever the resources
 * reload.
 */
public final class KartShadow {
    private KartShadow() {}

    public record Box(float x0, float x1, float z0, float z1) {}

    private static volatile Box box;

    public static Box get() {
        return box;
    }

    /** A reload listener's body. */
    public static void load(ResourceManager manager) {
        Optional<Resource> res = manager.getResource(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart/kart_shadow.json"));
        if (res.isEmpty()) {
            box = null;
            return;
        }
        try (Reader in = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            box = new Box(o.get("x0").getAsFloat(), o.get("x1").getAsFloat(), o.get("z0").getAsFloat(), o.get("z1").getAsFloat());
        } catch (Exception ex) {
            CtrCraftMod.LOGGER.warn("Could not read the kart's shadow: {}", ex.toString());
            box = null;
        }
    }
}
