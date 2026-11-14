package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.kart.KartFlags;
import com.ctrcraft.kart.KartWheels;
import com.ctrcraft.kart.KartScale;
import com.ctrcraft.physics.AirTilt;
import com.ctrcraft.physics.Wheelie;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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
    /** The baseline kart (blockbench/kart/kart_ctr.bbmodel, the original's proportions); the spawn item keeps the older item/kart model. */
    public static final ModelResourceLocation MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/kart_ctr"));
    /** The wheel (blockbench/kart/kart_wheel.bbmodel): an octagonal prism along x, drawn four times, spinning, the front two steering. */
    public static final ModelResourceLocation WHEEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/kart_wheel"));
    /** How far the front wheels turn at full lock, degrees. */
    private static final float WHEEL_LOCK = 25.0F;
    /** The wheel model's own size (blockbench/kart/kart_wheel.bbmodel): 3 pixels thick, 7 across. */
    private static final float WHEEL_THICKNESS = 3.0F / 16.0F;
    private static final float WHEEL_DIAMETER = 7.0F / 16.0F;
    /** The steering wheel (blockbench/kart/kart_ctr.bbmodel's steering_1 and up, made into a model of its own by tools/make_kart_ctr.py): turned about its axis by the steering. */
    public static final ModelResourceLocation STEERING =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/kart_steering"));
    /** The eight models of the boost flame (the original's animation; the jar has one shape for all). */
    public static final ModelResourceLocation[] FLAMES = new ModelResourceLocation[8];
    static {
        for (int k = 0; k < 8; k++) FLAMES[k] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_boost_flame_" + k));
    }
    /** The flame's outer shell, which the game draws subtractively (the pack has it; the jar's is empty). */
    public static final ModelResourceLocation[] FLAME_SHELLS = new ModelResourceLocation[8];
    static {
        for (int k = 0; k < 8; k++) FLAME_SHELLS[k] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/pickup_boost_flame_sub_" + k));
    }
    /** How far the wheel turns at full lock, degrees. */
    public static final float STEERING_LOCK = 25.0F;
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };

    private static final ResourceLocation SHADOW = ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "textures/entity/kart_shadow.png");
    /** The shadow's size in blocks: a little larger than the kart, as the original's (front at the top of the picture). */
    /** The shadow picture's own alpha (up to 0.5 to 0.75) is blended further: it read as a solid patch on light ground. */
    private static final int SHADOW_ALPHA = 190;

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
        // the shadow picture is made from the kart's own model seen from above (tools/make_kart_shadow.py) and lies where its json says; without that, the span of the wheels
        com.ctrcraft.kart.KartShadow.Box sb = com.ctrcraft.kart.KartShadow.get();
        float x0 = sb != null ? sb.x0() : -KartWheels.halfWidth();
        float x1 = sb != null ? sb.x1() : KartWheels.halfWidth();
        float z0 = sb != null ? sb.z0() : KartWheels.frontEdge();
        float z1 = sb != null ? sb.z1() : KartWheels.rearEdge();
        shadowVertex(vc, p, x0, z0, 0.0F, 0.0F, light);
        shadowVertex(vc, p, x0, z1, 0.0F, 1.0F, light);
        shadowVertex(vc, p, x1, z1, 1.0F, 1.0F, light);
        shadowVertex(vc, p, x1, z0, 1.0F, 0.0F, light);
        pose.popPose();
    }

    private static void shadowVertex(VertexConsumer vc, PoseStack.Pose p, float x, float z, float u, float v, int light) {
        vc.addVertex(p, x, 0.0F, z).setColor(255, 255, 255, SHADOW_ALPHA).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0.0F, 1.0F, 0.0F);
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
     * The jump pose of the kart's model (kart frame: x to its right, +z ahead, the model's front is -z, so the forward shift and the pitch flip, as the wheelie's do; a rotation about
     * the length axis, the roll, keeps its sense when z is flipped, and a yaw flips): a shift, then the three rotations the game's matrix holds, about the kart's own middle.
     */
    private static void applyAirTilt(PoseStack pose, AirTilt.Pose air) {
        pose.translate(air.x(), air.lift(), -air.forward());
        // seen in the original's footage (rear and front): the kart leans forward and to its LEFT, so the table's signs are taken the other way round from the wheelie's
        pose.mulPose(Axis.ZP.rotationDegrees((float) -air.rollDegrees()));
        pose.mulPose(Axis.YP.rotationDegrees((float) air.yawDegrees()));
        pose.mulPose(Axis.XP.rotationDegrees((float) air.pitchDegrees()));
    }

    /**
     * What the drive animation does to the driver at full lock, measured from its frames (tools/inspect_ctr_kart.py, Crash): the torso turns 19 degrees, the head 36, and the
     * head moves 23 units (0.21 of a crate) sideways and drops 6 about the waist, which is a lean of about 30 degrees; the legs and the kart do not move. The lean here is 20:
     * a Minecraft rider is much taller against the kart than the original's driver, and 30 swings the head too far.
     */
    public static final float BODY_TWIST = 19.0F;
    public static final float HEAD_TWIST = 36.0F;
    public static final float LEAN = 20.0F;
    /** The rider sits reclined (degrees back from upright, about the hips) with his legs raised that much more than the seated pose has them. */
    public static final float RECLINE = 18.0F;
    public static final float LEG_LIFT = 15.0F;

    /**
     * Turns the pose stack the way the kart's body is turned (the lean onto the ground, the boost wheelie and, after an explosion, the tumble), about the kart's own origin, for something that
     * is drawn at {@code offset} from it: the rider. The pose stack is in the world's axes at the rider's position; {@code offset} is the kart's position minus the rider's.
     */
    public static void applyBodyTurn(PoseStack pose, KartEntity kart, double ox, double oy, double oz, float partialTick) {
        int tumble = KartFlags.tumble(kart.getVisualFlags());
        int tilt = kart.getTilt();
        Wheelie.Pose wheelie = Wheelie.pose(KartFlags.wheelie(kart.getVisualFlags()), KartScale.LU_PER_BLOCK);
        AirTilt.Pose air = AirTilt.pose(KartFlags.airTilt(kart.getVisualFlags()), AirTilt.DEFAULT_CHARACTER, KartScale.LU_PER_BLOCK);
        if (air != null) wheelie = null;               // one baked pose at a time: the jump's replaces the wheelie's
        if (tumble == 0 && tilt == KartEntity.TILT_FLAT && wheelie == null && air == null) return;
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
        // the boost wheelie, as the kart's own model gets it (render): the rider is part of the kart, so it lifts, shifts, rears up and squashes with it
        if (wheelie != null) {
            pose.translate(0.0, wheelie.lift(), -wheelie.forward());
            pose.mulPose(Axis.XP.rotationDegrees((float) -wheelie.pitchDegrees()));
            pose.scale((float) wheelie.sx(), (float) wheelie.sy(), (float) wheelie.sz());
        }
        // the jump's pose moves the kart, not the driver: he is lifted and shifted with it but stays upright (the original's footage)
        if (air != null) pose.translate(air.x(), air.lift(), -air.forward());
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

    /** The four wheels at the kart's origin frame: each spins about its axle (x), the front two are turned into the steering first. */
    private void drawWheels(KartEntity entity, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
        BakedModel wheel = Minecraft.getInstance().getModelManager().getModel(WHEEL);
        float spin = entity.wheelSpin(partialTick);
        float steer = entity.steer() * WHEEL_LOCK;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        java.util.List<KartWheels.Wheel> list = KartWheels.get();
        for (KartWheels.Wheel w : list) {
            pose.pushPose();
            pose.translate(w.x(), w.y(), w.z());
            if (w.front()) pose.mulPose(Axis.YP.rotationDegrees(-steer));          // the model's front is -z: a right turn (+steer) is a negative turn about y
            pose.mulPose(Axis.XP.rotationDegrees(-spin));                        // rolling forward (towards -z) turns the top that way
            pose.scale(w.thickness() / WHEEL_THICKNESS, w.height() / WHEEL_DIAMETER, w.length() / WHEEL_DIAMETER);       // the marker's size: the model is 3 by 7 by 7 pixels
            pose.translate(-0.5, -0.5, -0.5);
            for (Direction side : SIDES) {
                random.setSeed(42L);
                for (var quad : wheel.getQuads(null, side, random)) {
                    consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
                }
            }
            pose.popPose();
        }
    }

    /**
     * The boost flame (VehFire / VehTurbo): while the kart's turbo is lit, a flame model at each exhaust pipe, the second one mirrored, scaled by fireSize / 8 (4 to 8), whose picture
     * crawls through the eight models of the animation (1.5 frames a tick, the second pipe three frames on) and which fades out as the turbo dies. Drawn bright and see-through (the game adds
     * it to the screen); the super turbo's is tinted blue. The model's origin is the middle of its root, which goes on the pipe's tip, the flame streaming back. Pose stack: the kart's frame.
     */
    private void drawBoostFlames(KartEntity entity, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        int flags = entity.getVisualFlags();
        int fire = KartFlags.fireSize(flags);
        float alpha = (8 - KartFlags.fade(flags)) / 8.0F;
        boolean ultra = KartFlags.has(flags, KartFlags.ULTRA);
        if (DebugCommands.flameSize > 0) {                    // /ctrdebug flame
            fire = DebugCommands.flameSize;
            alpha = 1.0F;
            ultra = DebugCommands.flameUltra;
        }
        if (fire <= 0) return;
        float lum = DebugCommands.flameBright;
        float r = (ultra ? 0.45F : 1.0F) * lum;
        float g = (ultra ? 0.65F : 1.0F) * lum;
        float b = lum;
        float size = fire / 8.0F;
        float time = (entity.tickCount + partialTick) * 1.5F;
        VertexConsumer add = buffers.getBuffer(AdditiveTypes.BLOCK_ATLAS);
        VertexConsumer take = buffers.getBuffer(AdditiveTypes.BLOCK_ATLAS_SUBTRACT);
        int i = 0;
        for (com.ctrcraft.kart.KartExhausts.Pipe pipe : com.ctrcraft.kart.KartExhausts.get()) {
            int frame = ((int) time + 3 * i) & 7;
            i++;
            pose.pushPose();
            // the pipes are measured from the kart's origin, and the pose stack here is in the baked model's frame (shifted by -0.5 in x and z): shift back
            pose.translate(pipe.x() + 0.5 + (pipe.x() < 0.0F ? -DebugCommands.flameDx : DebugCommands.flameDx), pipe.y() + DebugCommands.flameDy, pipe.z() + 0.5 + DebugCommands.flameDz);
            float turn = DebugCommands.flameRotX ? -1.0F : 1.0F;       // half a turn about x: y and z negated
            pose.scale((pipe.x() < 0.0F ? -size : size) * (DebugCommands.flameFlipX ? -1.0F : 1.0F), size * turn, size * turn * (DebugCommands.flameFlipZ ? -1.0F : 1.0F));
            pose.translate(-0.5, -0.5, -0.5);
            drawFlame(Minecraft.getInstance().getModelManager().getModel(FLAME_SHELLS[frame]), pose, take, alpha, alpha, alpha, alpha);
            drawFlame(Minecraft.getInstance().getModelManager().getModel(FLAMES[frame]), pose, add, r, g, b, alpha);
            pose.popPose();
        }
    }

    private void drawFlame(BakedModel flame, PoseStack pose, VertexConsumer vc, float r, float g, float b, float a) {
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : flame.getQuads(null, side, random)) {
                vc.putBulkData(pose.last(), quad, r, g, b, a, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
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
        AirTilt.Pose air = AirTilt.pose(KartFlags.airTilt(entity.getVisualFlags()), AirTilt.DEFAULT_CHARACTER, KartScale.LU_PER_BLOCK);
        if (air != null) {
            w = null;                                  // one baked pose at a time: the jump's replaces the wheelie's
            applyAirTilt(pose, air);
        }
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
        drawWheels(entity, pose, buffers, packedLight, partialTick);
        pose.translate(-0.5, 0.0, -0.5);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        // the faces with a tint index (grey scale in the texture) are multiplied by a colour of the kart's: index 0 (the tub and struts) its body colour, 1 (the seat) its seat colour
        int rgb = entity.colour();
        int seat = entity.seatColour();
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                int c = quad.getTintIndex() == 0 ? rgb : quad.getTintIndex() == 1 ? seat : 0xFFFFFF;
                consumer.putBulkData(pose.last(), quad, ((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
            }
        }
        // the steering wheel: its own model, turned about the axis of the wheel (through its bar's middle, along the way its face looks, towards the driver) by the steering; the rod,
        // which is part of the kart's model, stays. Seen from the driver a right turn (+steer) turns the wheel clockwise, a negative turn about that axis.
        com.ctrcraft.kart.KartSteering.Axis sa = com.ctrcraft.kart.KartSteering.get();
        if (sa != null) {
            BakedModel wheelModel = Minecraft.getInstance().getModelManager().getModel(STEERING);
            pose.pushPose();
            pose.translate(sa.px(), sa.py(), sa.pz());
            pose.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(-entity.steer() * STEERING_LOCK), sa.ax(), sa.ay(), sa.az()));
            pose.translate(-sa.px(), -sa.py(), -sa.pz());
            for (Direction side : SIDES) {
                random.setSeed(42L);
                for (var quad : wheelModel.getQuads(null, side, random)) {
                    consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
                }
            }
            pose.popPose();
        }
        drawBoostFlames(entity, pose, buffers, partialTick);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
