package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.kart.KartFlags;
import com.ctrcraft.kart.KartScale;
import com.ctrcraft.physics.Wheelie;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the kart with its baked item model ({@code models/item/kart.json}, generated from blockbench/kart/kart.bbmodel).
 * The model's front is north (-z); the kart's heading turns it.
 */
public class KartRenderer extends EntityRenderer<KartEntity> {
    private static final ModelResourceLocation MODEL =
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "kart"));
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };

    private final RandomSource random = RandomSource.create();

    public KartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public ResourceLocation getTextureLocation(KartEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(KartEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        pose.pushPose();
        // model front is -z; at yaw 0 the kart faces +z
        float theta = 180.0F - entity.renderYawDegrees(partialTick);
        pose.mulPose(Axis.YP.rotationDegrees(theta));
        // lean on the ground: turn the model's up onto the ground normal (x, z synced as bytes), seen from the model's own axes
        int tilt = entity.getTilt();
        if (tilt != KartEntity.TILT_FLAT) {
            double nx = ((tilt & 0xFF) - 127) / 127.0;
            double nz = (((tilt >> 8) & 0xFF) - 127) / 127.0;
            double ny = Math.sqrt(Math.max(0.0, 1.0 - nx * nx - nz * nz));
            double c = Math.cos(Math.toRadians(theta));
            double sn = Math.sin(Math.toRadians(theta));
            Vector3f local = new Vector3f((float) (nx * c - nz * sn), (float) ny, (float) (nx * sn + nz * c));
            pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), local));
        }
        // the boost wheelie: lift, forward shift, nose-up pitch and squash/stretch from CTR's own pose tables (kart frame: +z ahead,
        // the model's front is -z, so the forward shift and the pitch flip)
        Wheelie.Pose w = Wheelie.pose(KartFlags.wheelie(entity.getVisualFlags()), KartScale.LU_PER_BLOCK);
        if (w != null) {
            pose.translate(0.0, w.lift(), -w.forward());
            pose.mulPose(Axis.XP.rotationDegrees((float) -w.pitchDegrees()));
            pose.scale((float) w.sx(), (float) w.sy(), (float) w.sz());
        }
        pose.translate(-0.5, 0.0, -0.5);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
