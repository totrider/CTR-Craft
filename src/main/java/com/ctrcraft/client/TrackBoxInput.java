package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModItems;
import com.ctrcraft.track.TrackBoxPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Track Tool, pointing at a side of a node's box: right-click extends it by one block, left-click retracts it.
 * Done on the client's click (the box sides are not blocks, so vanilla would never send the server these clicks).
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class TrackBoxInput {
    private TrackBoxInput() {}

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() && !event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(ModItems.TRACK_TOOL.get())) return;
        BoxPicker.Pick pick = BoxPicker.pick(mc);
        if (pick == null) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        int delta = event.isAttack() ? -1 : 1;
        PacketDistributor.sendToServer(new TrackBoxPayload(pick.node(), pick.side(), delta));
    }
}
