package com.ctrcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Render types that ADD to the screen (the console's additive blend: the colour times its alpha is added to what is behind), for the boost flame. */
public final class AdditiveTypes extends RenderType {
    private AdditiveTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean affectsCrumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, affectsCrumbling, sort, setup, clear);
    }

    private static final RenderStateShard.TransparencyStateShard ADD = new RenderStateShard.TransparencyStateShard("ctrcraft_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /** The block atlas, lit by nothing, added to the screen, both faces, not written to the depth buffer (so flames do not hide each other or what is behind them). */
    public static final RenderType BLOCK_ATLAS = create("ctrcraft_additive_atlas", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(ADD)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));

    private static final RenderStateShard.TransparencyStateShard SUBTRACT = new RenderStateShard.TransparencyStateShard("ctrcraft_subtractive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        com.mojang.blaze3d.platform.GlStateManager._blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_REVERSE_SUBTRACT);
    }, () -> {
        com.mojang.blaze3d.platform.GlStateManager._blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /** The same, but the picture is TAKEN from the screen (the console's subtractive blend: the flame's outer shell, which is what leaves the red and orange). */
    public static final RenderType BLOCK_ATLAS_SUBTRACT = create("ctrcraft_subtractive_atlas", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(SUBTRACT)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));
}
