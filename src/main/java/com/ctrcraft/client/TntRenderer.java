package com.ctrcraft.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.TntEntity;
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

/**
 * The TNT power-up's crate: the model {@code item/pickup_tnt} (a Nitro crate: {@code item/pickup_nitro}), which by default is a cube with the Minecraft TNT block's textures
 * (as decided with the author), scaled down so it reads as a power-up and not a placed TNT block, tinted green for Nitro. A resource pack can replace the models: the
 * original's crate picture as a flat single-quad model is turned to face the camera, like the Wumpa fruit. On a kart it flashes.
 */
public class TntRenderer extends EntityRenderer<TntEntity> {
    public static final ModelResourceLocation TNT = model("pickup_tnt");
    public static final ModelResourceLocation NITRO = model("pickup_nitro");
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    private static final float SIZE = 0.8F;

    private final RandomSource random = RandomSource.create();
    private final Map<BakedModel, Boolean> sprites = new WeakHashMap<>();

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/" + name));
    }

    public TntRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(TntEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(entity.nitro() ? NITRO : TNT);
        boolean sprite = sprites.computeIfAbsent(model, this::isSprite);
        int fuse = entity.fuse();
        boolean flash = fuse >= 0 && (fuse / 5) % 2 == 0;
        float t = entity.tickCount + partialTick;
        pose.pushPose();
        net.minecraft.world.entity.Entity kart = entity.targetKart() >= 0 ? entity.level().getEntity(entity.targetKart()) : null;
        float headYaw = Float.NaN;
        if (kart != null && fuse >= 0) {
            // on the kart's head, every frame, from the kart's own smooth position (the crate's own updates come 20 times a second); and it goes where the head goes: the body's turn
            // (the lean on the slope, a jump, a steering lean) about the kart's origin, and the head's own turn into a corner
            net.minecraft.world.phys.Vec3 d = kart.getPosition(partialTick).subtract(entity.getPosition(partialTick));
            if (kart instanceof com.ctrcraft.kart.KartEntity k) {
                KartRenderer.applyBodyTurn(pose, k, d.x, d.y, d.z, partialTick);
                headYaw = -(k.renderYawDegrees(partialTick) + k.steer() * KartRenderer.HEAD_TWIST);
            }
            pose.translate(d.x, d.y, d.z);
            if (kart instanceof com.ctrcraft.kart.KartEntity k) {
                // the head leans with the upper body about the hips (the rider's own lean is RiderPlayerModel's)
                // in the kart's own frame (the model's front is -z): the recline (the top goes to +z) and the lean (a right turn leans right), about the hips
                float theta = 180.0F - k.renderYawDegrees(partialTick);
                pose.translate(0.0, com.ctrcraft.kart.KartEntity.HIP_HEIGHT, 0.0);
                pose.mulPose(Axis.YP.rotationDegrees(theta));
                pose.mulPose(Axis.ZP.rotationDegrees(-k.steer() * KartRenderer.LEAN));
                pose.mulPose(Axis.XP.rotationDegrees(KartRenderer.RECLINE));
                pose.mulPose(Axis.YP.rotationDegrees(-theta));
                pose.translate(0.0, -com.ctrcraft.kart.KartEntity.HIP_HEIGHT, 0.0);
            }
            pose.translate(0.0, com.ctrcraft.kart.KartEntity.HEAD_TOP, 0.0);
        }
        pose.scale(SIZE, SIZE, SIZE);
        if (sprite) {
            pose.translate(0.0, 0.5, 0.0);
            pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
            pose.translate(-0.5, -0.5, -0.5);
        } else {
            if (fuse < 0) TiltRender.apply(pose, entity.tilt());         // lies on the surface it came to rest on, facing the way the kart that dropped it faced
            pose.mulPose(Axis.YP.rotationDegrees(Float.isNaN(headYaw) ? -entity.getYRot() : headYaw));
            if (fuse >= 0) pose.translate(0.0, Math.sin(t * 0.5) * 0.04, 0.0);
            pose.translate(-0.5, 0.0, -0.5);
        }
        float r = 1.0F;
        float g = 1.0F;
        float b = 1.0F;
        if (entity.nitro() && model.getParticleIcon().contents().name().getNamespace().equals("minecraft")) {          // the default model, not the pack's green crate
            r = 0.45F;
            b = 0.45F;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        int overlay = OverlayTexture.pack(OverlayTexture.u(flash ? 1.0F : 0.0F), 10);
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                consumer.putBulkData(pose.last(), quad, r, g, b, 1.0F, light, overlay);
            }
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** True when every vertex of the model lies in one plane of constant z (a flat quad). */
    private boolean isSprite(BakedModel model) {
        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                int[] v = quad.getVertices();
                for (int i = 0; i < 4; i++) {
                    float z = Float.intBitsToFloat(v[i * 8 + 2]);
                    lo = Math.min(lo, z);
                    hi = Math.max(hi, z);
                }
            }
        }
        return lo <= hi && hi - lo < 1.0E-4F;
    }

    @Override
    public ResourceLocation getTextureLocation(TntEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
