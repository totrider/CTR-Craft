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

        // exhaust: the pipe sticks out of the back, 0.75 block behind the origin
        double ex = x - fx * 0.78;
        double ey = y + 0.28;
        double ez = z - fz * 0.78;
        int fire = KartFlags.fireSize(flags);
        if (fire > 0) {
            // CTR's flame is one model whose only variation is its size; it thins out as it fades. Blue is reserved for burning past
            // the sacred fire cap, which only a super turbo pad does in CTR.
            int fade = KartFlags.fade(flags);
            boolean ultra = KartFlags.has(flags, KartFlags.ULTRA);
            int n = 1 + fire / 2;
            for (int i = 0; i < n; i++) {
                if (rnd.nextInt(8) >= 8 - fade) {
                    continue;
                }
                double sp = 0.10 + rnd.nextDouble() * 0.12;
                double vx = -fx * sp + (rnd.nextDouble() - 0.5) * 0.04;
                double vz = -fz * sp + (rnd.nextDouble() - 0.5) * 0.04;
                double vy = (rnd.nextDouble() - 0.3) * 0.03;
                level.addParticle(ultra && (i & 1) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        ex + (rnd.nextDouble() - 0.5) * 0.06, ey, ez + (rnd.nextDouble() - 0.5) * 0.06, vx, vy, vz);
            }
        }
        if (KartFlags.has(flags, KartFlags.BACKFIRE)) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, ex, ey, ez,
                        -fx * 0.08 + (rnd.nextDouble() - 0.5) * 0.04, 0.03, -fz * 0.08 + (rnd.nextDouble() - 0.5) * 0.04);
            }
        }

        // tyre smoke: back wheels 0.34 behind, front 0.31 ahead, 0.47 to each side
        boolean back = KartFlags.has(flags, KartFlags.BACK_SKID);
        boolean front = KartFlags.has(flags, KartFlags.FRONT_SKID);
        if (back || front) {
            for (int side = -1; side <= 1; side += 2) {
                if (back) {
                    smoke(level, rnd, x - fx * 0.34 + lx * 0.47 * side, y + 0.05, z - fz * 0.34 + lz * 0.47 * side);
                }
                if (front) {
                    smoke(level, rnd, x + fx * 0.31 + lx * 0.47 * side, y + 0.05, z + fz * 0.31 + lz * 0.47 * side);
                }
            }
        }
        if (KartFlags.has(flags, KartFlags.WALL_RUB)) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.CRIT, x + (rnd.nextDouble() - 0.5) * 0.8, y + 0.3, z + (rnd.nextDouble() - 0.5) * 0.8,
                        (rnd.nextDouble() - 0.5) * 0.3, 0.12, (rnd.nextDouble() - 0.5) * 0.3);
            }
        }
    }

    private static void smoke(Level level, RandomSource rnd, double x, double y, double z) {
        level.addParticle(ParticleTypes.CLOUD, x, y, z, (rnd.nextDouble() - 0.5) * 0.02, 0.02, (rnd.nextDouble() - 0.5) * 0.02);
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
