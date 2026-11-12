package com.ctrcraft.client;

import org.lwjgl.glfw.GLFW;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Client-side registrations (mod bus): the kart renderer and the extra driving keys. */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class CtrCraftClient {
    private CtrCraftClient() {}

    private static final String CATEGORY = "key.categories.ctrcraft";

    /** L1: the second shoulder button (hop / powerslide / boost). R1 is the vanilla jump key. */
    public static final KeyMapping KEY_HOP2 =
            new KeyMapping("key.ctrcraft.hop2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    /** Square: brake (with the forward key a power-brake; with the back key, reverse). */
    public static final KeyMapping KEY_BRAKE =
            new KeyMapping("key.ctrcraft.brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    /** During a race: back to the last checkpoint you passed. */
    public static final KeyMapping KEY_RESPAWN =
            new KeyMapping("key.ctrcraft.respawn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.KART.get(), KartRenderer::new);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart_hud"), KartHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "race_hud"), RaceHud::render);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_HOP2);
        event.register(KEY_BRAKE);
        event.register(KEY_RESPAWN);
    }
}
