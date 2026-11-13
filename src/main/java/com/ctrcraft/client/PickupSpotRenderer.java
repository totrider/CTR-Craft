package com.ctrcraft.client;

import com.ctrcraft.ModBlocks;
import com.ctrcraft.ModItems;
import com.ctrcraft.pickup.PickupSpotBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pickup spot markers (fruit, fruit crate, power-up crate) are invisible blocks: this draws their model only while the player holds something to edit them
 * with (a spot block, the Track Tool, a node block or the Wrench). With anything else in hand the spots are not there to see; the pickups they hold are.
 */
public class PickupSpotRenderer implements BlockEntityRenderer<PickupSpotBlockEntity> {
    public PickupSpotRenderer(BlockEntityRendererProvider.Context context) {}

    /** Whether the item is one for building or editing a track. */
    public static boolean isEditingItem(ItemStack stack) {
        return stack.is(ModItems.TRACK_TOOL.get()) || stack.is(ModItems.WRENCH.get()) || stack.is(ModBlocks.START_NODE_ITEM.get())
                || stack.is(ModBlocks.CHECKPOINT_NODE_ITEM.get()) || stack.is(ModBlocks.FRUIT_SPOT_ITEM.get()) || stack.is(ModBlocks.FRUIT_CRATE_SPOT_ITEM.get())
                || stack.is(ModBlocks.POWERUP_CRATE_SPOT_ITEM.get());
    }

    public static boolean holdingEditingItem(Player player) {
        return isEditingItem(player.getMainHandItem()) || isEditingItem(player.getOffhandItem());
    }

    @Override
    public void render(PickupSpotBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !holdingEditingItem(mc.player)) return;
        BlockState state = be.getBlockState();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state, model, 1.0F, 1.0F, 1.0F, light,
                OverlayTexture.NO_OVERLAY);
    }
}
