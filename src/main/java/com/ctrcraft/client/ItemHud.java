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
        if (!shown.isEmpty()) {
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
