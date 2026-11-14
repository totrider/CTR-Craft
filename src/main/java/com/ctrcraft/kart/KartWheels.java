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
 * Where the kart's four wheels are and how big: {@code assets/ctrcraft/kart/kart_wheels.json}, written by tools/make_kart_ctr.py from the boxes named {@code wheel_front_left},
 * {@code wheel_front_right}, {@code wheel_rear_left} and {@code wheel_rear_right} in blockbench/kart/kart_ctr.bbmodel (markers: they are in a group with export off). Each has the
 * wheel's middle in blocks from the kart's origin (x to its right, y up, z forward negative as in the model) and its size: the thickness along the axle, the height and the length.
 * The wheel model (kart_wheel) is scaled to the size and drawn there; the wheels' radius is what the spin goes by; the shadow is as big as they span. Read again whenever the resources
 * reload.
 */
public final class KartWheels {
    private KartWheels() {}

    public record Wheel(float x, float y, float z, float thickness, float height, float length, boolean front) {}

    private static volatile List<Wheel> wheels = List.of(
            new Wheel(-0.5F, 0.21875F, -0.6875F, 0.1875F, 0.4375F, 0.4375F, true), new Wheel(0.5F, 0.21875F, -0.6875F, 0.1875F, 0.4375F, 0.4375F, true),
            new Wheel(-0.5F, 0.21875F, 0.3125F, 0.1875F, 0.4375F, 0.4375F, false), new Wheel(0.5F, 0.21875F, 0.3125F, 0.1875F, 0.4375F, 0.4375F, false));

    public static List<Wheel> get() {
        return wheels;
    }

    /** The wheels' radius, blocks: the mean of their half heights and half lengths (a turn of the wheel is this much road). */
    public static double radius() {
        double sum = 0.0;
        for (Wheel w : wheels) sum += (w.height() + w.length()) / 4.0;
        return sum / wheels.size();
    }

    /** Half the width the wheels span (the outer edges), blocks. */
    public static float halfWidth() {
        float m = 0.0F;
        for (Wheel w : wheels) m = Math.max(m, Math.abs(w.x()) + w.thickness() / 2.0F);
        return m;
    }

    /** The most forward edge of any wheel (z, negative: the model's front is -z). */
    public static float frontEdge() {
        float m = Float.MAX_VALUE;
        for (Wheel w : wheels) m = Math.min(m, w.z() - w.length() / 2.0F);
        return m;
    }

    /** The rearmost edge of any wheel (z). */
    public static float rearEdge() {
        float m = -Float.MAX_VALUE;
        for (Wheel w : wheels) m = Math.max(m, w.z() + w.length() / 2.0F);
        return m;
    }

    /** A reload listener's body. */
    public static void load(ResourceManager manager) {
        Optional<Resource> res = manager.getResource(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart/kart_wheels.json"));
        if (res.isEmpty()) return;
        try (Reader in = res.get().openAsReader()) {
            JsonArray arr = JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("wheels");
            List<Wheel> list = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                JsonArray p = o.getAsJsonArray("pos");
                JsonArray s = o.getAsJsonArray("size");
                list.add(new Wheel(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(), s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat(), o.get("front").getAsBoolean()));
            }
            if (list.size() == 4) {
                wheels = List.copyOf(list);
                CtrCraftMod.LOGGER.info("Kart wheels: {} (radius {})", wheels, radius());
            }
        } catch (Exception ex) {
            CtrCraftMod.LOGGER.warn("Could not read the kart's wheels: {}", ex.toString());
        }
    }
}
