package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.BombEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Bomb as a 3D model ({@code item/pickup_bomb}: the jar's stand-in from blockbench/pickups/bomb.bbmodel, the original's ball and fuse in the local resource pack) that rolls about its
 * x axis through the ball's middle and faces where it is heading, with its spark as a camera-facing glow at the tip of the fuse (textures/entity/bomb_spark.png, the tip from
 * {@link BombFuse}), pulsing; {@link BombFx} throws little flames off the tip. The juiced bomb is tinted red (the original's bomb is the same model; the blast is what changes).
 */
public class BombRenderer extends EntityRenderer<BombEntity> {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_bomb"));
    private static final ResourceLocation SPARK = ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "textures/entity/bomb_spark.png");
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };

    private final RandomSource random = RandomSource.create();

    public BombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(BombEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        BombFuse.Data fuse = BombFuse.get();
        float theta = BombFx.theta(entity, partialTick);
        float roll = BombFx.roll(entity, partialTick);
        float gb = entity.powered() ? 0.47F : 1.0F;
        pose.pushPose();
        pose.translate(0.0, fuse.ground(), 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(theta));
        pose.mulPose(Axis.XP.rotationDegrees(-roll));
        pose.translate(-0.5, -0.5, -0.5);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                consumer.putBulkData(pose.last(), quad, 1.0F, gb, gb, 1.0F, light, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();

        // the spark: a glow facing the camera at the fuse's tip, pulsing (the tip is in world axes from the bomb's origin)
        Vector3f tip = BombFx.tip(entity, partialTick);
        float t = entity.tickCount + partialTick;
        float s = fuse.sparkSize() * (0.8F + 0.2F * Mth.sin(t * 1.9F) + 0.08F * Mth.sin(t * 5.3F));
        pose.pushPose();
        pose.translate(tip.x, tip.y, tip.z);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(t * 23.0F));
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SPARK));
        float h = s / 2.0F;
        int bright = 15728880;
        vertex(vc, pose, m, -h, -h, 0.0F, 1.0F, bright);
        vertex(vc, pose, m, h, -h, 1.0F, 1.0F, bright);
        vertex(vc, pose, m, h, h, 1.0F, 0.0F, bright);
        vertex(vc, pose, m, -h, h, 0.0F, 0.0F, bright);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float y, float u, float v, int light) {
        vc.addVertex(m, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(BombEntity entity) {
        return SPARK;
    }
}
