package com.ctrcraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A clod of dirt flung up by a wheel on dirt: it tumbles, falls and bounces off the ground (the original's {@code clod} sprite). */
public class ClodParticle extends TextureSheetParticle {
    protected ClodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 14 + random.nextInt(8);
        this.quadSize = 0.07F + random.nextFloat() * 0.06F;
        this.gravity = 0.9F;
        this.friction = 0.92F;
        this.hasPhysics = true;
        this.roll = random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += onGround ? 0.0F : 0.35F;
        if (age > lifetime - 5) {
            this.alpha = Math.max(0.0F, (lifetime - age) / 5.0F);
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ClodParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
