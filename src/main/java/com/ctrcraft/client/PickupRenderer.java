package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.PickupEntity;
import com.ctrcraft.pickup.PickupKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import java.util.Map;
import java.util.WeakHashMap;

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
 * Draws a pickup spinning and bobbing in place with its baked model (models/item/pickup_*.json, generated from blockbench/pickups/*.bbmodel by
 * tools/make_pickup_models.py). The model's origin is the bottom centre of the block. A model that is a single flat plane is a sprite (a resource pack can replace the
 * fruit with its pre-rendered, animated spin): it is turned to face the camera instead of spinning.
 */
public class PickupRenderer extends EntityRenderer<PickupEntity> {
    public static final ModelResourceLocation WUMPA = model("pickup_wumpa");
    public static final ModelResourceLocation FRUIT_CRATE = model("pickup_fruit_crate");
    public static final ModelResourceLocation POWERUP_CRATE = model("pickup_powerup_crate");
    private static final Direction[] SIDES = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };

    private final RandomSource random = RandomSource.create();
    private final Map<BakedModel, Boolean> sprites = new WeakHashMap<>();

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "item/" + name));
    }

    public PickupRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(PickupEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        PickupKind kind = entity.kind();
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(
                kind == PickupKind.FRUIT ? WUMPA : kind == PickupKind.FRUIT_CRATE ? FRUIT_CRATE : POWERUP_CRATE);
        float t = entity.tickCount + partialTick;
        pose.pushPose();
        // the models are in the original's size already: a crate stands on the spot, the fruit floats a little
        pose.translate(0.0, kind == PickupKind.FRUIT ? 0.25 + Math.sin(t * 0.12) * 0.05 : 0.0, 0.0);
        boolean sprite = sprites.computeIfAbsent(model, this::isSprite);
        if (sprite) {
            pose.translate(0.0, 0.1, 0.0);           // the sprite's centre (the pack centres the fruit in its frame) is its pivot, floating with the fruit
            pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
            pose.translate(-0.5, -0.5, -0.5);
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(t * (kind == PickupKind.FRUIT ? 4.0F : 2.0F)));
            pose.translate(-0.5, 0.0, -0.5);
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** True when every vertex of the model lies in one plane of constant z (a flat quad). */
    private boolean isSprite(BakedModel model) {
        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (var quad : model.getQuads(null, side, random)) {
                int[] v = quad.getVertices();
                for (int i = 0; i < 4; i++) {
                    float z = Float.intBitsToFloat(v[i * 8 + 2]);
                    lo = Math.min(lo, z);
                    hi = Math.max(hi, z);
                }
            }
        }
        return lo <= hi && hi - lo < 1.0E-4F;
    }

    @Override
    public ResourceLocation getTextureLocation(PickupEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
