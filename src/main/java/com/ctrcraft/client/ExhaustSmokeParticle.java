package com.ctrcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A puff of exhaust smoke (VehEmitter_Exhaust): two a frame, one from each pipe, living 5 game frames, growing fast and fading. The pictures are the dust puff icons as white masks (their
 * brightness is the alpha). In the original's draw modes it is drawn SUBTRACTIVELY while the engine runs (the smoke darkens what is behind it: dark smoke) and additively when the turbo
 * meter is in the other state (it lightens it): so it is dark grey and alpha blended normally ({@code glow} false) and bright and additive ({@code glow} true) while boosting.
 */
public class ExhaustSmokeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean glow;
    private final float startSize;
    /** The kart the puff belongs to (the emitter's DRIVER_LOCAL flag, 0x800 of 0x08a3: the puff's place is in the driver's own frame, so it goes with the kart and turns with it) and its place and velocity in the kart's frame (blocks, x to its right, y up, z towards the back). */
    private com.ctrcraft.kart.KartEntity anchor;
    private double px;
    private double py;
    private double pz;
    private double vlx;
    private double vly;
    private double vlz;

    /** Ties the puff to a kart: {@code (x, y, z)} is where it starts in the kart's frame and {@code (vx, vy, vz)} how it moves there each tick. */
    public void anchor(com.ctrcraft.kart.KartEntity kart, double x, double y, double z, double vx, double vy, double vz) {
        this.anchor = kart;
        this.px = x;
        this.py = y;
        this.pz = z;
        this.vlx = vx;
        this.vly = vy;
        this.vlz = vz;
    }

    /** The puff's world place from the kart's frame at {@code partialTick}: the kart's own (interpolated) position and drawn yaw turn its frame. */
    private void place(float partialTick) {
        if (anchor == null || anchor.isRemoved()) return;
        double yaw = Math.toRadians(anchor.renderYawDegrees(partialTick));
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double mx = px + vlx * partialTick;
        double my = py + vly * partialTick;
        double mz = pz + vlz * partialTick;
        net.minecraft.world.phys.Vec3 k = anchor.getPosition(partialTick);
        this.x = k.x - lx * mx - fx * mz;                  // the model's frame: x to the kart's right (minus left), z to the back (minus forward)
        this.y = k.y + my;
        this.z = k.z - lz * mx - fz * mz;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTicks) {
        place(partialTicks);
        super.render(buffer, camera, partialTicks);
    }

    protected ExhaustSmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean glow) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.glow = glow;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 4;                                       // 5 game frames are 3.3 ticks; the last bit is nearly nothing
        this.startSize = 0.19F + random.nextFloat() * 0.03F;           // half the quad, blocks: the original's icon quad is 48 units across at the start (0.66 block) of which the soft puff fills about a half
        this.quadSize = startSize;
        this.alpha = 0.5F;
        this.friction = 0.92F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.roll = random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        if (glow) {
            this.rCol = 0.95F;                                   // the additive puff: a light grey (the emitter gives it no colour of its own)
            this.gCol = 0.95F;
            this.bCol = 0.95F;
        } else {
            this.rCol = 0.0F;                                    // the subtractive one takes light away: black, blended by its mask
            this.gCol = 0.0F;
            this.bCol = 0.0F;
        }
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        if (anchor != null) {
            // in the kart's frame it moves by itself (up and back); the world place comes from the kart each frame (render), so only the age goes on here
            px += vlx;
            py += vly;
            pz += vlz;
            vlx *= friction;
            vly *= friction;
            vlz *= friction;
            if (age++ >= lifetime || anchor.isRemoved()) remove();
        } else {
            super.tick();
        }
        setSpriteFromAge(sprites);
        // the emitter's axes: a size of 1536 growing 400 a frame (x2.3 in 5 frames) and a brightness of 16384 falling 2560 a frame, of 32768 as the full colour (128 = 1.0 in the console's colour
        // modulation, so a half): 0.5, 0.42, 0.34, 0.27, 0.19 over the five frames; a game frame is 2/3 of a tick
        float frames = age * 1.5F;
        this.quadSize = startSize * (1.0F + 400.0F * frames / 1536.0F);
        this.alpha = Math.max(0.0F, (16384.0F - 2560.0F * frames) / 32768.0F);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return glow ? 15728880 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return glow ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "CTRCRAFT_EXHAUST_ADDITIVE";
        }
    };

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean glow;

        public Provider(SpriteSet sprites, boolean glow) {
            this.sprites = sprites;
            this.glow = glow;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ExhaustSmokeParticle(level, x, y, z, vx, vy, vz, sprites, glow);
        }
    }
}
