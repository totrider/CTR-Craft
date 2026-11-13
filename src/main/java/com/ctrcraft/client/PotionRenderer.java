package com.ctrcraft.client;

import com.ctrcraft.pickup.PotionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

/** N. Brio's Beaker, drawn as a vanilla potion bottle (the green one, or the red one a kart with ten wumpa throws), turning slowly. */
public class PotionRenderer extends EntityRenderer<PotionEntity> {
    private static final ItemStack GREEN = stack(0x3CD030);
    private static final ItemStack RED = stack(0xD02020);

    public PotionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    private static ItemStack stack(int rgb) {
        ItemStack s = new ItemStack(Items.POTION);
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(0xFF000000 | rgb), List.of()));
        return s;
    }

    @Override
    public void render(PotionEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        TiltRender.apply(pose, entity.tilt());
        pose.translate(0.0, 0.4, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 6.0F));
        pose.scale(1.4F, 1.4F, 1.4F);
        Minecraft.getInstance().getItemRenderer().renderStatic(entity.red() ? RED : GREEN, ItemDisplayContext.GROUND, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PotionEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
