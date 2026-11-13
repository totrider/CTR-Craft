package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.BombEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The Bomb as a billboard: an atlas of 8 columns (the camera's side: 0 from behind, which is what a chase camera sees of a bomb going away, 4 from in front) by 32 rows
 * (one full turn of the bomb about its axle) in {@code textures/entity/bomb_roll.png}, 64 px each; the mod's stand-in is drawn by tools/make_bomb_sprites.py, the original's
 * rendering from the player's own disc comes in a local resource pack. The bomb rolls 0x200 of 4096 per frame, 67.5 degrees a game tick, so a tick is exactly six rows;
 * the row follows the roll and runs the other way for a bomb rolling backwards (the model still faces the way the kart did). The column follows where the camera is
 * relative to the bomb's heading, in 45 degree steps.
 */
public class BombRenderer extends EntityRenderer<BombEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "textures/entity/bomb_roll.png");
    private static final int FRAMES = 32;
    private static final int AZIMUTHS = 8;
    /** The picture spans the bomb's reach (body and fuse); the body is about a third of it. */
    private static final float SIZE = 2.4F;

    public BombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(BombEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float roll = (entity.tickCount + partialTick) * 67.5F * (entity.backward() ? 1.0F : -1.0F);
        int frame = Math.floorMod(Math.round(roll / (360.0F / FRAMES)), FRAMES);
        float v0 = frame / (float) FRAMES;
        float v1 = (frame + 1) / (float) FRAMES;
        // the column: where the camera stands in the bomb's own frame (x across it, y the way it faces); yaw = atan2(-x, -y), 0 = from behind (see tools/render_ctr_model.roll_atlas)
        double heading = Math.toRadians(net.minecraft.util.Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()));
        net.minecraft.world.phys.Vec3 cam = this.entityRenderDispatcher.camera.getPosition().subtract(entity.getPosition(partialTick));
        double across = cam.x * Math.cos(heading) + cam.z * Math.sin(heading);
        double along = cam.x * -Math.sin(heading) + cam.z * Math.cos(heading);
        int column = Math.floorMod((int) Math.round(Math.atan2(-across, -along) / (Math.PI * 2.0) * AZIMUTHS), AZIMUTHS);
        float u0 = column / (float) AZIMUTHS;
        float u1 = (column + 1) / (float) AZIMUTHS;
        // the juiced bomb is tinted red (the original's bomb is the same model; the blast is what changes)
        int g = entity.powered() ? 120 : 255;
        pose.pushPose();
        pose.translate(0.0, 0.5, 0.0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        float h = SIZE / 2.0F;
        vertex(vc, pose, m, -h, -h, u0, v1, g, light);
        vertex(vc, pose, m, h, -h, u1, v1, g, light);
        vertex(vc, pose, m, h, h, u1, v0, g, light);
        vertex(vc, pose, m, -h, h, u0, v0, g, light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float y, float u, float v, int g, int light) {
        vc.addVertex(m, x, y, 0.0F).setColor(255, g, g, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(BombEntity entity) {
        return TEXTURE;
    }
}
