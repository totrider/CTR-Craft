package com.ctrcraft.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Drawing HUD pictures of any size: a resource pack may replace the mod's icons with ones of another size or shape (the original game's are
 * 32 x 48), so a picture is fitted into its box by its own proportions, whatever they are. Sizes are read from the PNG header, once.
 */
public final class GuiTextures {
    private GuiTextures() {}

    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
    private static final int[] NONE = new int[0];

    /** The picture's size (width, height), or null when the pack has no such texture. */
    public static int[] size(ResourceLocation tex) {
        int[] s = SIZES.computeIfAbsent(tex, GuiTextures::read);
        return s.length == 0 ? null : s;
    }

    private static int[] read(ResourceLocation tex) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return NONE;
        try (InputStream in = res.get().open()) {
            byte[] head = in.readNBytes(24);
            if (head.length < 24) return NONE;
            int w = ((head[16] & 255) << 24) | ((head[17] & 255) << 16) | ((head[18] & 255) << 8) | (head[19] & 255);
            int h = ((head[20] & 255) << 24) | ((head[21] & 255) << 16) | ((head[22] & 255) << 8) | (head[23] & 255);
            return w > 0 && h > 0 ? new int[] { w, h } : NONE;
        } catch (java.io.IOException e) {
            return NONE;
        }
    }

    /** Forget what was read (a resource pack change can replace the pictures). */
    public static void clear() {
        SIZES.clear();
    }

    private static final ResourceLocation WUMPA_SPRITE = ResourceLocation.fromNamespaceAndPath("ctrcraft", "item/wumpa_sprite");

    /**
     * Draws the Wumpa fruit centred on (cx, cy) in a square of {@code size} pixels: the animated spinning fruit of the pickup (the {@code item/wumpa_sprite} of a resource
     * pack, a strip of frames the block atlas plays) when there is one, otherwise the static HUD picture {@code fallback}. The sprite's frame is much larger than the
     * fruit in it, so it is drawn twice the box to make the fruit as large as the static picture.
     */
    public static void drawWumpa(GuiGraphics g, ResourceLocation fallback, int cx, int cy, int size) {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(WUMPA_SPRITE);
        if (sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
            drawFit(g, fallback, cx - size / 2, cy - size / 2, size, size);
            return;
        }
        int s = size * 2;
        g.blit(cx - s / 2, cy - s / 2, 0, s, s, sprite);
    }

    /** Draws the picture over exactly the given rectangle (stretched to it): for pictures whose proportions are the original's reference pixels, not the window's. */
    public static boolean drawStretched(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        if (size(tex) == null) return false;
        g.blit(tex, x, y, w, h, 0.0F, 0.0F, 1, 1, 1, 1);
        return true;
    }

    /** Draws the picture as large as fits in the box at (x, y) of boxW x boxH, centred, keeping its proportions. Returns false if there is none. */
    public static boolean drawFit(GuiGraphics g, ResourceLocation tex, int x, int y, int boxW, int boxH) {
        int[] s = size(tex);
        if (s == null) return false;
        float k = Math.min(boxW / (float) s[0], boxH / (float) s[1]);
        int w = Math.max(1, Math.round(s[0] * k));
        int h = Math.max(1, Math.round(s[1] * k));
        g.blit(tex, x + (boxW - w) / 2, y + (boxH - h) / 2, w, h, 0.0F, 0.0F, 1, 1, 1, 1);
        return true;
    }
}
