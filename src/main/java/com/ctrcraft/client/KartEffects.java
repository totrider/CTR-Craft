package com.ctrcraft.client;

import java.util.HashMap;
import java.util.Map;

import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.kart.KartFlags;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Particles and looping sounds of a kart, from its synced flags, once a game tick, for every kart (yours and other players').
 * CTR's own effects: the turbo flame sized 5 + (fireLevel >> 6), the backfire puff of a boost pressed too early (the exhaust
 * for 8 frames), tyre smoke under the brake and through a powerslide, wall sparks.
 */
public final class KartEffects {
    private KartEffects() {}

    private static final Map<Integer, KartEngineSound> ENGINES = new HashMap<>();
    private static final Map<Integer, KartSkidSound> SKIDS = new HashMap<>();
    private static final Map<Integer, KartTurboSound> TURBOS = new HashMap<>();
    /** Where each kart's wheels were on the last tick they skidded (front left, front right, rear left, rear right), or null: the marks are drawn between there and the next place. */
    private static final Map<Integer, net.minecraft.world.phys.Vec3[]> SKID_LAST = new HashMap<>();

    public static void tick(KartEntity kart) {
        Level level = kart.level();
        if (!level.isClientSide) {
            return;
        }
        boolean driven = !kart.getPassengers().isEmpty();
        manageLoops(kart, driven);
        if (!driven) {
            return;
        }
        int flags = kart.getVisualFlags();
        RandomSource rnd = level.random;
        double yaw = Math.toRadians(kart.renderYawDegrees(1.0F));
        // Minecraft: forward = (-sin yaw, cos yaw), left = (cos yaw, sin yaw)
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double x = kart.getX();
        double y = kart.getY();
        double z = kart.getZ();

        // the exhaust pipes (KartExhausts, the open ends of the exhaust_* cubes of kart_ctr.bbmodel): the model's frame is x to the kart's right (minus the left vector), z to the back (minus forward)
        int exhaustMode = KartFlags.exhaust(flags);          // 0 none, 1 bright additive, 2 dark: from the sim's powerslide meter and flame, or the grid's revving engine
        for (com.ctrcraft.kart.KartExhausts.Pipe pipe : com.ctrcraft.kart.KartExhausts.get()) {
            double ex = x - lx * pipe.x() - fx * pipe.z();
            double ey = y + pipe.y();
            double ez = z - lz * pipe.x() - fz * pipe.z();
            double dxw = -lx * pipe.dx() - fx * pipe.dz();
            double dzw = -lz * pipe.dx() - fz * pipe.dz();
            // the smoke of VehEmitter_Exhaust: one puff from each pipe every game frame (1.5 to a tick), moving up and back at 4 + 4 units a frame (0.117 block a tick along the pipe, a
            // little spread), in the draw mode the sim says: bright additive or dark (and none in the cases where the original has none)
            if (exhaustMode != 0) {
                int puffs = 1 + (rnd.nextBoolean() ? 1 : 0);
                for (int k = 0; k < puffs; k++) {
                    // the puff is the kart's (DRIVER_LOCAL): it starts at the pipe's tip and moves in the kart's own frame, so it stays with the kart and turns with it
                    net.minecraft.core.particles.ParticleOptions type = exhaustMode == 2 ? com.ctrcraft.ModParticles.EXHAUST_SMOKE.get() : com.ctrcraft.ModParticles.EXHAUST_GLOW.get();
                    var made = Minecraft.getInstance().particleEngine.createParticle(type, ex, ey, ez, 0.0, 0.0, 0.0);
                    if (made instanceof ExhaustSmokeParticle puff) {
                        puff.anchor(kart, pipe.x(), pipe.y(), pipe.z(), pipe.dx() * 0.117 + (rnd.nextDouble() - 0.5) * 0.03, pipe.dy() * 0.117 + (rnd.nextDouble() - 0.5) * 0.03,
                                pipe.dz() * 0.117 + (rnd.nextDouble() - 0.5) * 0.03);
                    }
                }
            }
            // the boost flame is the original's model (VehTurbo), drawn by KartRenderer at each pipe, not particles
            if (KartFlags.has(flags, KartFlags.BACKFIRE)) {
                for (int i = 0; i < 2; i++) {
                    level.addParticle(com.ctrcraft.ModParticles.DUST_PUFF.get(), ex, ey, ez,
                            -fx * 0.08 + (rnd.nextDouble() - 0.5) * 0.04, 0.03, -fz * 0.08 + (rnd.nextDouble() - 0.5) * 0.04);
                }
            }
        }

        // skidmarks (VehGroundSkids): while the rear wheels skid (a drift, a power brake) or the front ones, each skidding wheel that is on the ground lays a strip from where it was a tick ago
        // to where it is, on the ground under it (the original's last for 24 frames and fade; SkidMarkParticle). The wheels are at the markers of the kart's model (KartWheels).
        boolean skidBack = KartFlags.has(flags, KartFlags.BACK_SKID);
        boolean skidFront = KartFlags.has(flags, KartFlags.FRONT_SKID);
        boolean onGround = !KartFlags.has(flags, KartFlags.AIRBORNE);
        net.minecraft.world.phys.Vec3[] last = SKID_LAST.computeIfAbsent(kart.getId(), k -> new net.minecraft.world.phys.Vec3[4]);
        java.util.List<com.ctrcraft.kart.KartWheels.Wheel> wheelList = com.ctrcraft.kart.KartWheels.get();
        for (int i = 0; i < 4 && i < wheelList.size(); i++) {
            com.ctrcraft.kart.KartWheels.Wheel w = wheelList.get(i);
            if (!(onGround && (w.front() ? skidFront : skidBack))) {
                last[i] = null;
                continue;
            }
            net.minecraft.world.phys.Vec3 cur = new net.minecraft.world.phys.Vec3(x - lx * w.x() - fx * w.z(), y + 0.03, z - lz * w.x() - fz * w.z());
            if (last[i] != null) {
                double d2 = last[i].distanceToSqr(cur);
                if (d2 > 1.0E-4 && d2 < 9.0) {
                    level.addParticle(com.ctrcraft.ModParticles.SKID_MARK.get(), cur.x, cur.y, cur.z, last[i].x - cur.x, last[i].y - cur.y, last[i].z - cur.z);
                }
            }
            last[i] = cur;
        }

        // clods come off the wheels: the rear pair this far behind the origin, the front pair this far ahead, this far to each side (KartWheels, from the markers of kart_ctr.bbmodel)
        double rearBack = 0.0;
        double frontAhead = 0.0;
        double sideOut = 0.0;
        for (com.ctrcraft.kart.KartWheels.Wheel w : com.ctrcraft.kart.KartWheels.get()) {
            if (w.front()) frontAhead = Math.max(frontAhead, -w.z());
            else rearBack = Math.max(rearBack, w.z());
            sideOut = Math.max(sideOut, Math.abs(w.x()));
        }
        if (KartFlags.has(flags, KartFlags.WALL_RUB)) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(com.ctrcraft.ModParticles.SMOKE_RING.get(), x + (rnd.nextDouble() - 0.5) * 0.8, y + 0.3, z + (rnd.nextDouble() - 0.5) * 0.8,
                        (rnd.nextDouble() - 0.5) * 0.2, 0.08, (rnd.nextDouble() - 0.5) * 0.2);
            }
        }

        // clods: a moving kart's wheels throw them up from dirt (CTR's dirt terrain emitters, VehEmitter_Terrain_Ground)
        double moved = Math.hypot(x - kart.xo, z - kart.zo);
        if (moved > 0.35) {
            net.minecraft.world.level.block.state.BlockState under = level.getBlockState(net.minecraft.core.BlockPos.containing(x, y - 0.2, z));
            if (under.is(com.ctrcraft.ModTags.SURFACE_DIRT) || under.is(com.ctrcraft.ModTags.SURFACE_SLOW_DIRT)) {
                int n = 1 + (int) Math.min(3.0, moved * 3.0);
                for (int i = 0; i < n; i++) {
                    int side = rnd.nextBoolean() ? 1 : -1;
                    double sp = 0.06 + rnd.nextDouble() * 0.10;
                    level.addParticle(com.ctrcraft.ModParticles.CLOD.get(),
                            x - fx * rearBack + lx * sideOut * side, y + 0.08, z - fz * rearBack + lz * sideOut * side,
                            -fx * sp + lx * side * 0.04 + (rnd.nextDouble() - 0.5) * 0.03, 0.12 + rnd.nextDouble() * 0.12,
                            -fz * sp + lz * side * 0.04 + (rnd.nextDouble() - 0.5) * 0.03);
                }
            }
        }
    }


    /** One engine loop and one tyre-skid loop per driven kart, started when somebody sits in it, gone when they leave. */
    private static void manageLoops(KartEntity kart, boolean driven) {
        Minecraft mc = Minecraft.getInstance();
        int id = kart.getId();
        if (driven) {
            KartEngineSound engine = ENGINES.get(id);
            if (engine == null || engine.isStopped()) {
                engine = new KartEngineSound(kart);
                ENGINES.put(id, engine);
                mc.getSoundManager().play(engine);
            }
            KartSkidSound skid = SKIDS.get(id);
            if (skid == null || skid.isStopped()) {
                skid = new KartSkidSound(kart);
                SKIDS.put(id, skid);
                mc.getSoundManager().play(skid);
            }
            KartTurboSound turbo = TURBOS.get(id);
            if (turbo == null || turbo.isStopped()) {
                turbo = new KartTurboSound(kart);
                TURBOS.put(id, turbo);
                mc.getSoundManager().play(turbo);
            }
        } else {
            ENGINES.remove(id);
            SKIDS.remove(id);
            TURBOS.remove(id);
        }
    }
}
