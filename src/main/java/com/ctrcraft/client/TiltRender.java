package com.ctrcraft.client;

import com.ctrcraft.pickup.GroundTilt;
import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Turns a pose stack so that "up" is the surface normal a placed weapon lies on (see {@link GroundTilt}). */
final class TiltRender {
    private TiltRender() {}

    static void apply(PoseStack pose, int tilt) {
        if (tilt == GroundTilt.FLAT) return;
        double nx = ((tilt & 0xFF) - 127) / 127.0;
        double nz = (((tilt >> 8) & 0xFF) - 127) / 127.0;
        double ny = Math.sqrt(Math.max(0.0, 1.0 - nx * nx - nz * nz));
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float) nx, (float) ny, (float) nz)));
    }
}
