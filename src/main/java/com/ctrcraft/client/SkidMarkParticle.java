package com.ctrcraft.client;

import com.ctrcraft.kart.KartWheels;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * One piece of a skidmark (VehGroundSkids): a strip of the ground the width of the wheel, from where the wheel was a tick ago to where it is, lying flat, that stays 16 ticks (the original's is a
 * shorter tail of six game frames) and fades out at the end. The particle's position is the new end of the strip, its velocity arguments are the vector to the old end.
 * Drawn from both sides (two quads, one each way round) so it does not matter how the ground is seen.
 */
public class SkidMarkParticle extends TextureSheetParticle {
    /** How long a strip stays: 16 ticks (0.8 s), longer than the original's tail of six game frames, which reads better here: solid at first, then fading out. */
    private static final int LIFETIME = 16;
    private final Vec3 from;
    private final float halfWidth;

    protected SkidMarkParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.from = new Vec3(x + dx, y + dy, z + dz);
        float thick = 0.0F;
        for (KartWheels.Wheel w : KartWheels.get()) thick += w.thickness();
        this.halfWidth = Math.max(0.06F, thick / KartWheels.get().size() * 0.6F);
        this.lifetime = LIFETIME;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.rCol = 0.0F;
        this.gCol = 0.0F;
        this.bCol = 0.0F;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        // it does not move
        if (age++ >= lifetime) remove();
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        double sx = this.from.x - this.x;
        double sz = this.from.z - this.z;
        double len = Math.sqrt(sx * sx + sz * sz);
        if (len < 1.0E-4) return;
        // across the strip: horizontal, at right angles to it
        double nx = -sz / len * halfWidth;
        double nz = sx / len * halfWidth;
        float age = (this.age + partialTicks) / (float) lifetime;
        float a = 0.62F * (age < 0.45F ? 1.0F : Math.max(0.0F, 1.0F - (age - 0.45F) / 0.55F));
        int light = getLightColor(partialTicks);
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        double[][] p = {
            { this.x - nx, this.y, this.z - nz, u0, v1 },
            { this.x + nx, this.y, this.z + nz, u1, v1 },
            { this.from.x + nx, this.from.y, this.from.z + nz, u1, v0 },
            { this.from.x - nx, this.from.y, this.from.z - nz, u0, v0 },
        };
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                double[] c = p[pass == 0 ? i : 3 - i];
                buffer.addVertex((float) (c[0] - cam.x), (float) (c[1] - cam.y), (float) (c[2] - cam.z)).setUv((float) c[3], (float) c[4]).setColor(0.0F, 0.0F, 0.0F, a).setLight(light);
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new SkidMarkParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
