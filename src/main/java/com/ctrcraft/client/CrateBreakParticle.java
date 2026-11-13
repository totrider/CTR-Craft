package com.ctrcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A crate breaking: its pieces (client/CrateBreak) fly out over the animation's frames, the game stepping one frame per 1/30 s (1.5 a tick), so it is over in 8 ticks; between two
 * frames the corners are blended. The particle's position is the crate's base; its x velocity is the turn about the vertical axis in degrees (the weapon crate's break is turned
 * at random in the original). Every piece is drawn from both sides (two quads, one each way round: no state to change).
 */
public class CrateBreakParticle extends Particle {
    private static final float FRAMES_PER_TICK = 1.5F;
    private final CrateBreak.Data data;
    private final float cos;
    private final float sin;

    protected CrateBreakParticle(ClientLevel level, double x, double y, double z, double turnDegrees, CrateBreak.Data data) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.data = data;
        this.hasPhysics = false;
        this.lifetime = Math.max(1, (int) Math.ceil(data.frames() / FRAMES_PER_TICK));
        this.cos = Mth.cos((float) Math.toRadians(turnDegrees));
        this.sin = Mth.sin((float) Math.toRadians(turnDegrees));
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float t = (age + partialTicks) * FRAMES_PER_TICK;
        int f0 = Math.min((int) t, data.frames() - 1);
        int f1 = Math.min(f0 + 1, data.frames() - 1);
        float k = Math.min(1.0F, t - f0);
        float ox = (float) (x - cam.x());
        float oy = (float) (y - cam.y());
        float oz = (float) (z - cam.z());
        int light = getLightColor(partialTicks);
        float[] p = new float[9];
        for (CrateBreak.Triangle tri : data.triangles()) {
            if (tri.pos().length <= f1) continue;
            for (int i = 0; i < 3; i++) {
                float px = Mth.lerp(k, tri.pos()[f0][i * 3], tri.pos()[f1][i * 3]);
                float py = Mth.lerp(k, tri.pos()[f0][i * 3 + 1], tri.pos()[f1][i * 3 + 1]);
                float pz = Mth.lerp(k, tri.pos()[f0][i * 3 + 2], tri.pos()[f1][i * 3 + 2]);
                p[i * 3] = ox + px * cos - pz * sin;
                p[i * 3 + 1] = oy + py;
                p[i * 3 + 2] = oz + px * sin + pz * cos;
            }
            float[] uv = tri.uv();
            // one way round, then the other (the last corner twice: a quad that is a triangle)
            for (int pass = 0; pass < 2; pass++) {
                int[] order = pass == 0 ? new int[] { 0, 1, 2, 2 } : new int[] { 2, 1, 0, 0 };
                for (int c : order) {
                    buffer.addVertex(p[c * 3], p[c * 3 + 1], p[c * 3 + 2]).setUv(uv[c * 2], uv[c * 2 + 1]).setColor(1.0F, 1.0F, 1.0F, 1.0F).setLight(light);
                }
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return SHEET;
    }

    private static final ParticleRenderType SHEET = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
            CrateBreak.Data d = CrateBreak.get();
            RenderSystem.depthMask(true);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            if (d != null) RenderSystem.setShaderTexture(0, d.texture());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "CTRCRAFT_CRATE_BREAK";
        }
    };

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double turn, double vy, double vz) {
            CrateBreak.Data d = CrateBreak.get();
            return d == null ? null : new CrateBreakParticle(level, x, y, z, turn, d);
        }
    }
}
