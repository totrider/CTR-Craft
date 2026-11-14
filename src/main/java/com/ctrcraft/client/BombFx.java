package com.ctrcraft.client;

import com.ctrcraft.pickup.BombEntity;
import com.mojang.math.Axis;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The bomb's roll and where its fuse tip is. The bomb rolls 0x200 of 4096 a game frame (RB_MovingExplosive: dir.x += 0x200), 67.5 degrees a game tick, about the x axis of its model
 * through the middle of the ball, the other way for a bomb rolling backwards; the fuse and its spark are part of that turn, so the tip sweeps a circle round the axle. Used by the
 * renderer (the model, the spark billboard) and by the little flames and smoke thrown off the tip.
 */
public final class BombFx {
    private BombFx() {}

    private static final float DEGREES_PER_TICK = 67.5F;

    /** The roll in degrees (positive: the top turns towards the way it goes). */
    public static float roll(BombEntity bomb, float partialTick) {
        return (bomb.tickCount + partialTick) * DEGREES_PER_TICK * (bomb.backward() ? -1.0F : 1.0F);
    }

    /** The model's turn about the vertical: its front (-z) is where the bomb is heading. */
    public static float theta(BombEntity bomb, float partialTick) {
        return 180.0F - Mth.rotLerp(partialTick, bomb.yRotO, bomb.getYRot());
    }

    /** The fuse tip from the bomb's origin (on the ground under the ball), in world axes, blocks. */
    public static Vector3f tip(BombEntity bomb, float partialTick) {
        BombFuse.Data d = BombFuse.get();
        Vector3f v = new Vector3f(d.tipX(), d.tipY(), d.tipZ());
        Axis.XP.rotationDegrees(-roll(bomb, partialTick)).transform(v);      // the top turns towards -z, the front
        Axis.YP.rotationDegrees(theta(bomb, partialTick)).transform(v);
        v.y += d.ground();
        return v;
    }

    /** A little fire off the fuse's tip every tick, smoke now and then. */
    public static void tick(BombEntity bomb) {
        if (!bomb.level().isClientSide) return;
        Vector3f t = tip(bomb, 0.0F);
        Vec3 p = bomb.position().add(t.x, t.y, t.z);
        var rnd = bomb.level().random;
        bomb.level().addParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, (rnd.nextDouble() - 0.5) * 0.03, 0.02 + rnd.nextDouble() * 0.03, (rnd.nextDouble() - 0.5) * 0.03);
        if (bomb.tickCount % 3 == 0) {
            bomb.level().addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0.0, 0.03, 0.0);
        }
    }
}
