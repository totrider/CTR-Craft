package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * The rider turns with the kart: in a drift or a spin (the model faces the drawn yaw, not the heading), on a slope, when it rears up in a boost, and when the kart tumbles after an explosion, the player's model is turned about the kart's origin by the same turn the kart's
 * model gets (it used to sit upright whatever the kart did). Only the drawn model moves; the player's position and the camera do not, so nothing about the view changes.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class PlayerKartPose {
    private PlayerKartPose() {}

    @SubscribeEvent
    public static void onRender(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof KartEntity kart)) return;
        float pt = event.getPartialTick();
        // the rider faces the way the kart's model is drawn (in a drift or a spin that is not the way it heads), body and head
        float yaw = kart.renderYawDegrees(pt);
        // the legs stay with the kart; the head turns into the turn (the torso's own turn and the lean are in RiderPlayerModel), +steer being right, which is a larger yaw
        float head = yaw + kart.steer() * KartRenderer.HEAD_TWIST;
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.yHeadRot = head;
        player.yHeadRotO = head;
        Vec3 d = kart.getPosition(pt).subtract(player.getPosition(pt));
        KartRenderer.applyBodyTurn(event.getPoseStack(), kart, d.x, d.y, d.z, pt);
    }
}
