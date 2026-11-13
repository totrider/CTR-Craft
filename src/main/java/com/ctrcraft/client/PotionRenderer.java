package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.PotionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * N. Brio's Beaker: the model {@code item/pickup_beaker_green} (or {@code _red}, the one a kart with ten wumpa throws), built in Blockbench
 * ({@code blockbench/pickups/beaker_*.bbmodel}, written by tools/make_beaker_models.py): a round-bottomed flask from boxes. A resource pack can replace the models, for
 * instance with the original's mesh. It turns slowly, lies on the slope it came to rest on, and pops in from half size over four frames as the original's does
 * ({@code RB_GenericMine_ThTick}: scale 0x800, plus 0x200 a frame up to 0x1000). The bubbles that rise in it are billboards ({@link BeakerBubbles}).
 */
public class PotionRenderer extends EntityRenderer<PotionEntity> {
    public static final ModelResourceLocation GREEN = model("pickup_beaker_green");
    public static final ModelResourceLocation RED = model("pickup_beaker_red");
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };
    /**
     * The model is in blocks already, and a crate is a whole block here: the original's flask is 94 units tall (0.87 of a block) beside the crate's 109, so drawn at its own scale it
     * stays in proportion to the crates. (The entity's box, 0.5 x 0.6, is a hit box, not the picture's size.)
     */
    private static final float SIZE = 1.0F;

    private final RandomSource random = RandomSource.create();

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/" + name));
    }

    public PotionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(PotionEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(entity.red() ? RED : GREEN);
        float t = entity.tickCount + partialTick;
        // 0x800 + 0x200 a frame of 0x1000, a game tick being 1.5 frames
        float pop = Math.min(1.0F, 0.5F + 0.125F * t * 1.5F);
        // the bubbles first, and opaque: the flask is see-through (every texel of the original's beaker is), drawn over them
        BeakerBubbles.Data bubbles = BeakerBubbles.get(entity.red());
        if (bubbles != null) renderBubbles(entity, bubbles, t, pop, pose, buffers, light);
        pose.pushPose();
        TiltRender.apply(pose, entity.tilt());
        pose.mulPose(Axis.YP.rotationDegrees(t * 6.0F));
        pose.scale(SIZE * pop, SIZE * pop, SIZE * pop);
        pose.translate(-0.5, 0.0, -0.5);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /**
     * The bubbles: camera-facing quads rising through the flask, at the frame the game's animation would be on (one frame per 1/30 s, fifteen of them, from the flask's
     * birth); between two frames the middle is blended, but not across the wrap, where a bubble jumps from the top back to the bottom. Their positions are in the flask's own
     * frame, so they turn, tilt and pop in with it.
     */
    private void renderBubbles(PotionEntity entity, BeakerBubbles.Data data, float t, float pop, PoseStack pose, MultiBufferSource buffers, int light) {
        float frame = (t * 1.5F) % data.frames();
        int f0 = (int) frame;
        int f1 = (f0 + 1) % data.frames();
        float k = frame - f0;
        float s = SIZE * pop;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(data.texture()));
        org.joml.Vector3f world = new org.joml.Vector3f();
        for (BeakerBubbles.Bubble b : data.bubbles()) {
            float[] a = b.centres()[f0];
            float[] c = b.centres()[f1];
            boolean wrap = Math.abs(c[1] - a[1]) > 0.3F;
            float x = wrap ? a[0] : a[0] + (c[0] - a[0]) * k;
            float y = wrap ? a[1] : a[1] + (c[1] - a[1]) * k;
            float z = wrap ? a[2] : a[2] + (c[2] - a[2]) * k;
            // where the bubble is from the flask's origin: tilt, spin, scale, worked out on a stack of its own (the pose stack here already holds the flask's place in the world,
            // which would be added twice)
            PoseStack local = new PoseStack();
            TiltRender.apply(local, entity.tilt());
            local.mulPose(Axis.YP.rotationDegrees(t * 6.0F));
            local.scale(s, s, s);
            local.last().pose().transformPosition(x, y, z, world);
            pose.pushPose();
            pose.translate(world.x, world.y, world.z);
            pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
            org.joml.Matrix4f m = pose.last().pose();
            float hw = b.width() * s / 2.0F;
            float hh = b.height() * s / 2.0F;
            vertex(vc, pose, m, -hw, -hh, b.u0(), b.v1(), light);
            vertex(vc, pose, m, hw, -hh, b.u1(), b.v1(), light);
            vertex(vc, pose, m, hw, hh, b.u1(), b.v0(), light);
            vertex(vc, pose, m, -hw, hh, b.u0(), b.v0(), light);
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, org.joml.Matrix4f m, float x, float y, float u, float v, int light) {
        vc.addVertex(m, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(PotionEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
