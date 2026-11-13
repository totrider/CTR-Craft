package com.ctrcraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The starburst of an exploding bomb: the spiky burst picture of the original's shared icon pack (a stand-in without the pack), swelling fast and fading from yellow
 * to red in about 0.4 s. The blast's radius arrives as the x velocity; the burst is about half as wide as the blast is deep. A flash, so fully bright.
 */
public class BurstParticle extends TextureSheetParticle {
    private final float fullSize;

    protected BurstParticle(ClientLevel level, double x, double y, double z, double size, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.fullSize = (float) Math.max(0.5, size) * 0.3F;
        this.quadSize = fullSize * 0.4F;
        this.lifetime = 8;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.roll = random.nextFloat() * 0.3F - 0.15F;
        this.oRoll = this.roll;
        this.rCol = 1.0F;
        this.gCol = 0.95F;
        this.bCol = 0.5F;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = age / (float) lifetime;
        this.quadSize = fullSize * (0.4F + 0.6F * Math.min(1.0F, life * 3.0F));
        this.gCol = 0.95F - 0.55F * life;
        this.bCol = Math.max(0.0F, 0.5F - 0.5F * life * 2.0F);
        this.alpha = Math.max(0.0F, 1.0F - life * life);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double size, double vy, double vz) {
            return new BurstParticle(level, x, y, z, size, sprites);
        }
    }
}
