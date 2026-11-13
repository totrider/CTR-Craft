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

    private static final ResourceLocation SHADOW = ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "textures/entity/kart_shadow.png");
    /** The shadow's size in blocks: a little larger than the kart, as the original's (front at the top of the picture). */
    private static final float SHADOW_HALF_WIDTH = 0.8F;
    private static final float SHADOW_HALF_LENGTH = 1.0F;

    private final RandomSource random = RandomSource.create();

    /**
     * VehGroundShadow: a dark kart-shaped patch on the ground straight under the kart, at the same size however high the kart is, which is what makes it useful
     * for judging a landing. It lies on the ground it finds below (down to 48 blocks), leaning with the kart's smoothed ground normal.
     */
    private void renderGroundShadow(KartEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var level = entity.level();
        double ex = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double ey = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY());
        double ez = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(ex, ey + 0.4, ez);
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, from.add(0.0, -48.0, 0.0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, entity));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return;
        }
        float theta = 180.0F - entity.renderYawDegrees(partialTick);
        pose.pushPose();
        pose.translate(0.0, hit.getLocation().y - ey + 0.02, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(theta));
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
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SHADOW));
        PoseStack.Pose p = pose.last();
        float hw = SHADOW_HALF_WIDTH;
        float hl = SHADOW_HALF_LENGTH;
        shadowVertex(vc, p, -hw, -hl, 0.0F, 0.0F, light);
        shadowVertex(vc, p, -hw, hl, 0.0F, 1.0F, light);
        shadowVertex(vc, p, hw, hl, 1.0F, 1.0F, light);
        shadowVertex(vc, p, hw, -hl, 1.0F, 0.0F, light);
        pose.popPose();
    }

    private static void shadowVertex(VertexConsumer vc, PoseStack.Pose p, float x, float z, float u, float v, int light) {
        vc.addVertex(p, x, 0.0F, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0.0F, 1.0F, 0.0F);
    }

    public KartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;   // the kart has its own shadow, see renderGroundShadow
    }

    @Override
    public ResourceLocation getTextureLocation(KartEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /**
     * Turns the pose stack the way the kart's body is turned (the lean onto the ground and, after an explosion, the tumble), about the kart's own origin, for something that
     * is drawn at {@code offset} from it: the rider. The pose stack is in the world's axes at the rider's position; {@code offset} is the kart's position minus the rider's.
     */
    public static void applyBodyTurn(PoseStack pose, KartEntity kart, double ox, double oy, double oz, float partialTick) {
        int tumble = KartFlags.tumble(kart.getVisualFlags());
        int tilt = kart.getTilt();
        if (tumble == 0 && tilt == KartEntity.TILT_FLAT) return;
        float theta = 180.0F - kart.renderYawDegrees(partialTick);
        pose.translate(ox, oy, oz);
        pose.mulPose(Axis.YP.rotationDegrees(theta));
        if (tilt != KartEntity.TILT_FLAT) {
            double nx = ((tilt & 0xFF) - 127) / 127.0;
            double nz = (((tilt >> 8) & 0xFF) - 127) / 127.0;
            double ny = Math.sqrt(Math.max(0.0, 1.0 - nx * nx - nz * nz));
            double c = Math.cos(Math.toRadians(theta));
            double sn = Math.sin(Math.toRadians(theta));
            pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float) (nx * c - nz * sn), (float) ny, (float) (nx * sn + nz * c))));
        }
        if (tumble != 0) {
            int f = tumble & 63;
            double swing = Math.sin(2.0 * Math.PI * 3.0 * f / 27.0);
            pose.translate(0.0, 0.3, 0.0);
            pose.mulPose(Axis.ZP.rotationDegrees(f * 720.0F / 27.0F));
            pose.mulPose(Axis.XP.rotationDegrees((float) (-swing * 360.0 / 7.0)));
            pose.translate(0.0, -0.3, 0.0);
        }
        pose.mulPose(Axis.YP.rotationDegrees(-theta));
        pose.translate(-ox, -oy, -oz);
    }

    @Override
    public void render(KartEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        renderGroundShadow(entity, partialTick, pose, buffers, packedLight);
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
        // after an explosion the kart plays the blasted animation (the table Vector.c bakes at start-up, 27 frames of 32 ms): a roll about its own length axis of two
        // turns, a pitch wobble of three swings (up to 51 degrees), and a squash and stretch (x by a quarter, z by 15 %), all about its middle
        int tumble = KartFlags.tumble(entity.getVisualFlags());
        if (tumble != 0) {
            int f = tumble & 63;
            double swing = Math.sin(2.0 * Math.PI * 3.0 * f / 27.0);
            double wobble = Math.sin(2.0 * Math.PI * 2.0 * f / 27.0);
            pose.translate(0.0, 0.3, 0.0);
            pose.mulPose(Axis.ZP.rotationDegrees(f * 720.0F / 27.0F));
            pose.mulPose(Axis.XP.rotationDegrees((float) (-swing * 360.0 * (4096.0 / 7.0) / 4096.0)));
            pose.scale((float) (1.0 + wobble / 4.0), 1.0F, (float) (1.0 + wobble * 6.0 / 40.0));
            pose.translate(0.0, -0.3, 0.0);
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
