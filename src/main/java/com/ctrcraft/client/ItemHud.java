package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.items.HeldItem;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.pickup.ItemClient;
import com.ctrcraft.pickup.ItemManager;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The held power-up and the Wumpa count, where the original game draws them (512 x 216 reference screen, scaled to the window): the power-up's icon
 * top left of centre at (232, 5), cycling through the icons while the roulette runs, the Wumpa fruit centred at (316, 24), then "x" and the count.
 * The fruit flies in from the pickup ({@link WumpaFly}). A power-up from a data pack without an icon of its own (textures/gui/power/&lt;id&gt;.png in its namespace) shows its name instead.
 */
public final class ItemHud {
    private ItemHud() {}

    private static final int ORANGE = 0xFFFF8A1F;
    private static final int JUICED = 0xFFFF5A1F;
    /** UI_Weapon_DrawBG: with ten wumpa a shining spiky burst sits behind the fruit and the held power-up (the shared pack's picture coloured as in the original, pulsing). */
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "textures/gui/glow.png");
    private static final ResourceLocation WUMPA = ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "textures/gui/wumpa.png");

    /** The icon texture of a power-up id, or null if there is none. With ten wumpa the "juiced up" picture is used where the pack has one (<id>_juiced). */
    private static ResourceLocation icon(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        if (ItemClient.wumpa() >= ItemManager.JUICED) {
            ResourceLocation juiced = rl.withPath("textures/gui/power/" + rl.getPath() + "_juiced.png");
            if (GuiTextures.size(juiced) != null) return juiced;
        }
        ResourceLocation tex = rl.withPath("textures/gui/power/" + rl.getPath() + ".png");
        return GuiTextures.size(tex) != null ? tex : null;
    }

    /**
     * UI_WeaponBG_DrawShine twice (UI_WeaponBG_AnimateShine colours). The picture is a quarter burst, drawn four times mirrored round the centre as a gouraud quad: the bright
     * colour at the centre corner, the middle one at the two side corners and the dark one at the far corner. One pass subtracts a grey of 0x5f to 0xbe from every channel, the other adds the colour (yellow in
     * the middle, orange, dark red at the tips: red and green rise and fall together with |sin|), and the subtraction comes first: clamped at black, the colour added to it is
     * vivid and not a pale sum. Every texel of the picture is blended (all have the semi-transparency bit); the linear filtering is what softens its rim. The picture is the original's, its grey 0 to 189.
     */
    /** The part of the shine's colour range that is used, 0 (dull) to 1 (full): taken from the footage, where it reads as a mild flicker of orange to yellow. */
    private static final float SHINE_LOW = 0.35F;
    private static final float SHINE_HIGH = 0.75F;

    /** The held power-up the HUD is still showing after it was used (the original keeps it 30 frames, flickering: noItemTimer), and until when. */
    private static String lastHeld = "";
    private static long ghostUntil;

    private static void glow(GuiGraphics g, float cx, float cy, float w, float h) {
        if (GuiTextures.size(GLOW) == null) return;
        // The game steps the phase twice a frame (once in each draw call), so its |sin| runs through its whole range in about four frames and the eye averages it into a mild
        // flicker; drawn at its true range and a slow period it is a big swing, so the range is squeezed (SHINE_LOW to SHINE_HIGH of it)
        float s = SHINE_LOW + (SHINE_HIGH - SHINE_LOW) * (float) Math.abs(Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI / 0.27));
        int yellow = (int) (127 + 127 * s);
        int orange = (int) (50 + 50 * s);
        int darkRed = (int) (33 + 33 * s);
        int darkGreen = (int) (16 + 16 * s);
        int gray = (int) (95 + 95 * s);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        // the second primitive is drawn FIRST (AddPrim puts it at the head of the ordering table): the grey is taken off the screen, clamped at black, and then the colour is added
        // to that, which is why the result is vivid and not a pale sum
        com.mojang.blaze3d.platform.GlStateManager._blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_REVERSE_SUBTRACT);
        // the PS1 modulates a texel by colour / 128 (up to twice as bright), a GL colour by colour / 255: each pass is drawn twice, which adds up to the same
        int[][] greys = { { gray, gray, gray }, { gray, gray, gray }, { gray, gray, gray } };
        quarters(g, cx, cy, w, h, greys);
        quarters(g, cx, cy, w, h, greys);
        com.mojang.blaze3d.platform.GlStateManager._blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        int[][] colours = { { darkRed, darkGreen, 0 }, { yellow, orange, 0 }, { yellow, yellow, 0 } };
        quarters(g, cx, cy, w, h, colours);
        quarters(g, cx, cy, w, h, colours);
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    }

    /** The four mirrored quarters of the burst; {@code colours} are the dark, middle and bright vertex colours (0 to 255 each). */
    private static void quarters(GuiGraphics g, float cx, float cy, float w, float h, int[][] colours) {
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, GLOW);
        com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        org.joml.Matrix4f m = g.pose().last().pose();
        float hw = w / 2.0F;
        float hh = h / 2.0F;
        for (int qy = 0; qy < 2; qy++) {
            for (int qx = 0; qx < 2; qx++) {
                float x0 = cx - hw + qx * hw;
                float y0 = cy - hh + qy * hh;
                int uL = qx == 0 ? 0 : 1;
                int uR = 1 - uL;
                int vT = qy == 0 ? 0 : 1;
                int vB = 1 - vT;
                // top left, bottom left, bottom right, top right: the order the GUI's own quads use (the other way round is a back face, which culling drops)
                vertex(bb, m, x0, y0, uL, vT, colours);
                vertex(bb, m, x0, y0 + hh, uL, vB, colours);
                vertex(bb, m, x0 + hw, y0 + hh, uR, vB, colours);
                vertex(bb, m, x0 + hw, y0, uR, vT, colours);
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void vertex(com.mojang.blaze3d.vertex.BufferBuilder bb, org.joml.Matrix4f m, float x, float y, int u, int v, int[][] colours) {
        int[] c = colours[u + v];           // (0,0) the far corner: dark; the two side corners: middle; (1,1) the centre: bright
        bb.addVertex(m, x, y, 0.0F).setUv(u, v).setColor(c[0], c[1], c[2], 255);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof KartEntity)) {
            WumpaFly.reset();
            return;
        }
        if (mc.options.hideGui) return;
        float sy = g.guiHeight() / 216.0F;
        float sx = KartHud.PIXEL_ASPECT * sy;                       // the same proportions in every window; centred on the screen
        float mid = g.guiWidth() / 2.0F - 256.0F * sx;               // the reference x of 0 on the screen

        String shown = "";
        boolean rolling = ItemClient.rolling();
        if (rolling) {
            // the roulette cycles through the power-ups a race can give, whatever resource pack is on (one without an icon shows its name); the cut ones and the
            // battle-only ones never show
            java.util.List<HeldItem> cycle = new java.util.ArrayList<>();
            for (HeldItem i : HeldItem.values()) {
                if (i != HeldItem.SPRING && i != HeldItem.INVISIBILITY && i != HeldItem.SUPER_ENGINE) cycle.add(i);
            }
            shown = cycle.get((int) ((ItemClient.rollingForMs() / 90) % cycle.size())).id();
        } else if (!ItemClient.held().isEmpty()) {
            shown = ItemClient.held();
        }
        // using an item: the original fires it at once but keeps the icon and its box for 30 frames (a second), drawn only every other frame
        boolean flickerOff = false;
        if (rolling) {
            lastHeld = "";
            ghostUntil = 0;
        } else if (!ItemClient.held().isEmpty()) {
            lastHeld = ItemClient.held();
            ghostUntil = 0;
        } else if (!lastHeld.isEmpty()) {
            long now = System.currentTimeMillis();
            if (ghostUntil == 0) ghostUntil = now + 1000L;
            if (now < ghostUntil) {
                shown = lastHeld;
                flickerOff = ((now / 33L) & 1L) == 0L;
            } else {
                lastHeld = "";
                ghostUntil = 0;
            }
        }
        // measured on the original's screen: the box is 43 x 27 reference pixels at (235, 7), its bottom level with the fruit's; the icon is 48 x 32, centred on the
        // box, so it sticks out of it a little
        int bx = Math.round(mid + 235 * sx);
        int by = Math.round(7 * sy);
        int bw = Math.round(43 * sx);
        int bh = Math.round(27 * sy);
        int ix = Math.round(mid + 232.5F * sx);
        int iy = Math.round(4.5F * sy);
        int iw = Math.round(48 * sx);
        int ih = Math.round(32 * sy);
        if (ItemClient.wumpa() >= ItemManager.JUICED && GuiTextures.size(GLOW) != null) {
            // The sizes are measured on the footage, with the real picture mocked up at them beside it: the item's spike tips reach about a quarter of its frame past the top
            // and bottom with the valleys on the frame's outline (the burst is twice the frame's height); the fruit's burst is about 1.5 times the fruit's height and centred on
            // its body, which is lower than the point the sprite is placed at (the leaves are on top). The widths are the earlier ones.
            if (!shown.isEmpty()) glow(g, bx + bw / 2.0F, by + bh / 2.0F, bw * 2.3F, bh * 2.0F);
            glow(g, mid + 316 * sx, 25.5F * sy, 64 * sx, 30 * sy);
        }
        if (!shown.isEmpty() && !flickerOff) {
            // the original's box behind the icon: dark blue, half transparent, with a clear darker outline
            int o = Math.max(1, Math.round(sy * 0.8F));
            g.fill(bx - o, by - o, bx + bw + o, by + bh + o, 0xE00A1030);
            g.fill(bx, by, bx + bw, by + bh, 0xA0303C78);
            ResourceLocation icon = icon(shown);
            if (icon == null || !GuiTextures.drawStretched(g, icon, ix, iy, iw, ih)) {
                String name = ItemManager.nameOf(shown).getString();
                g.drawString(mc.font, name, bx, by + bh / 2 - 4, rolling ? 0xFFB8B0C8 : 0xFFFFFFFF, true);
            }
        }

        WumpaFly.update(ItemClient.wumpa(), mc.player.getVehicle().getPosition(delta.getGameTimeDeltaPartialTick(false)), g.guiWidth(), g.guiHeight(), mc.isPaused());
        int wumpa = ItemClient.wumpa() - WumpaFly.pending();                // the count goes up as each fruit arrives
        int colour = ItemClient.wumpa() >= ItemManager.JUICED ? JUICED : ORANGE;
        GuiTextures.drawWumpa(g, WUMPA, Math.round(mid + 316 * sx), Math.round(23 * sy), Math.round(20 * sy));
        KartHud.outlined(g, mc, "x", Math.round(mid + 336 * sx), Math.round(20 * sy), sy, colour);
        KartHud.outlined(g, mc, Integer.toString(wumpa), Math.round(mid + 349 * sx), Math.round(17 * sy), 1.6F * sy, colour);
        if (WumpaFly.drawing()) {
            GuiTextures.drawWumpa(g, WUMPA, Math.round(mid + WumpaFly.x() * sx), Math.round(WumpaFly.y() * sy), Math.round(20 * sy));
        }
    }
}
