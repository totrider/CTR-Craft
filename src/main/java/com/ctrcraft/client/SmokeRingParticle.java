package com.ctrcraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A bright starburst that swells and dies in a few ticks: the original's {@code smokering} sprite, used here for the sparks of a kart rubbing a wall.
 * (Where the original uses that sprite is not in the decompilation's code; its look, a glowing star with a dark halo, is that of a spark.)
 */
public class SmokeRingParticle extends TextureSheetParticle {
    protected SmokeRingParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 5 + random.nextInt(4);
        this.quadSize = 0.14F + random.nextFloat() * 0.08F;
        this.gravity = 0.15F;
        this.hasPhysics = false;
        this.roll = random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.quadSize *= 1.12F;
        this.alpha = Math.max(0.0F, 1.0F - age / (float) lifetime);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;   // full bright: it is a flash
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
            return new SmokeRingParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
