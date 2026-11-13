package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;

import java.lang.reflect.Field;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The player's model while driving a kart: the driver's upper body steers and the rest stays with the kart, as the original's drive animation does (measured from its frames,
 * tools/inspect_ctr_kart.py: at full lock the torso turns 19 degrees, the head 36, and the upper body leans about the waist; the legs, the seat and the kart do not move).
 * Done after the normal pose is worked out (this model replaces the player renderers' own): the torso turns and leans about the hips, the head follows the lean and keeps the
 * turn the renderer gave it, and the hands stay on the wheel: each arm is aimed from its (moved) shoulder at a fixed point in front of the seat, as a straight arm can,
 * since a Minecraft arm has no elbow. The sleeves, jacket and hat copy their parts, and armour and held items follow the parts as always.
 */
public class RiderPlayerModel extends PlayerModel<AbstractClientPlayer> {
    private static final float DEG = (float) Math.PI / 180.0F;
    /** The hips, in the model's pixels below the shoulders: the upper body pivots here. */
    private static final float HIP = 12.0F;
    /** Where the hands hold the wheel, pixels from the shoulder line's middle (x to the player's left, y down, z towards the back): in front of the lap. */
    private static final float HAND_X = 3.0F;
    private static final float HAND_Y = 12.0F;
    private static final float HAND_Z = -7.0F;

    public RiderPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!(player.getVehicle() instanceof KartEntity kart)) return;
        float steer = kart.steer();
        float twist = steer * KartRenderer.BODY_TWIST * DEG;          // + turns to the right
        float lean = -steer * KartRenderer.LEAN * DEG;                // a positive roll leans the top to the player's left: a right turn leans right

        float[] p = place(0.0F, 0.0F, 0.0F, twist, lean);
        body.setPos(p[0], p[1], p[2]);
        body.yRot = twist;
        body.zRot = lean;
        head.setPos(p[0], p[1], p[2]);
        head.zRot = lean;

        arm(leftArm, 1.0F, twist, lean);
        arm(rightArm, -1.0F, twist, lean);

        hat.copyFrom(head);
        jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
    }

    /** A point of the upper body (pixels from its resting pivot) after the turn about the vertical through the spine and the lean about the hips. */
    private static float[] place(float x, float y, float z, float twist, float lean) {
        float x1 = x * (float) Math.cos(twist) + z * (float) Math.sin(twist);
        float z1 = -x * (float) Math.sin(twist) + z * (float) Math.cos(twist);
        float dy = y - HIP;
        float x2 = x1 * (float) Math.cos(lean) - dy * (float) Math.sin(lean);
        float y2 = HIP + x1 * (float) Math.sin(lean) + dy * (float) Math.cos(lean);
        return new float[] { x2, y2, z1 };
    }

    /** An arm from its moved shoulder to the wheel: a straight arm of the model's length pointing at the hand's place (x first, then z about the length: ZYX order, y unused). */
    private void arm(ModelPart arm, float side, float twist, float lean) {
        var rest = arm.getInitialPose();
        float[] s = place(rest.x, rest.y, rest.z, twist, lean);
        arm.setPos(s[0], s[1], s[2]);
        float dx = side * HAND_X - s[0];
        float dy = HAND_Y - s[1];
        float dz = HAND_Z - s[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len;
        dy /= len;
        dz /= len;
        float c = (float) Math.sqrt(Math.max(1.0E-4F, 1.0F - dz * dz));
        arm.xRot = (float) Math.asin(Math.max(-1.0F, Math.min(1.0F, dz)));
        arm.yRot = 0.0F;
        arm.zRot = -(float) Math.asin(Math.max(-1.0F, Math.min(1.0F, dx / c)));
    }

    /** Puts this model into both player renderers (wide and slim arms). The renderers' layers ask the renderer for its model, so armour and items follow. */
    public static void install(EntityRenderersEvent.AddLayers event) {
        try {
            Field field = LivingEntityRenderer.class.getDeclaredField("model");
            field.setAccessible(true);
            for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer == null) continue;
                boolean slim = skin == PlayerSkin.Model.SLIM;
                field.set(renderer, new RiderPlayerModel(event.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            CtrCraftMod.LOGGER.warn("The rider's steering pose is off (could not replace the player model): {}", e.toString());
        }
    }
}
