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

    /** Hold to leave a race (sneaking does not get you out of the kart in one, so it cannot happen by accident). */
    public static final KeyMapping KEY_LEAVE =
            new KeyMapping("key.ctrcraft.leave_race", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, CATEGORY);

    @SubscribeEvent
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(TntRenderer.TNT);
        event.register(TntRenderer.NITRO);
        event.register(PickupRenderer.WUMPA);
        event.register(PickupRenderer.FRUIT_CRATE);
        event.register(PickupRenderer.POWERUP_CRATE);
    }

    @SubscribeEvent
    public static void registerReloadListener(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> GuiTextures.clear());
    }

    @SubscribeEvent
    public static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.ctrcraft.ModParticles.DUST_PUFF.get(), DustPuffParticle.Provider::new);
        event.registerSpriteSet(com.ctrcraft.ModParticles.CLOD.get(), ClodParticle.Provider::new);
        event.registerSpriteSet(com.ctrcraft.ModParticles.SMOKE_RING.get(), SmokeRingParticle.Provider::new);
    }

    /** Use the held power-up. */
    public static final KeyMapping KEY_ITEM =
            new KeyMapping("key.ctrcraft.item", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.KART.get(), KartRenderer::new);
        event.registerEntityRenderer(ModEntities.PICKUP.get(), PickupRenderer::new);
        event.registerEntityRenderer(ModEntities.TNT.get(), TntRenderer::new);
        event.registerEntityRenderer(ModEntities.POTION.get(), PotionRenderer::new);
        event.registerBlockEntityRenderer(com.ctrcraft.ModBlockEntities.PICKUP_SPOT.get(), PickupSpotRenderer::new);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart_hud"), KartHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "race_hud"), RaceHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item_hud"), ItemHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "screen_flash"), ScreenFlash::render);
    }

    @SubscribeEvent
    public static void clientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        RaceMusic.tick();
        RouletteSound.update();
    }

    @SubscribeEvent
    public static void captureCamera(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        WumpaFly.capture(event);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_HOP2);
        event.register(KEY_BRAKE);
        event.register(KEY_RESPAWN);
        event.register(KEY_LEAVE);
        event.register(KEY_ITEM);
    }
}
