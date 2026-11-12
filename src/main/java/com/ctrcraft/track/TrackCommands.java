package com.ctrcraft.track;

import com.ctrcraft.CtrCraftMod;
import com.mojang.brigadier.Command;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /ctrcraft track reset}: clears what test drives recorded for the start node nearest to you (to record the main lap again). */
@EventBusSubscriber(modid = CtrCraftMod.MODID)
public final class TrackCommands {
    private TrackCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ctrcraft").then(Commands.literal("track").then(Commands.literal("reset").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            TrackGraph graph = TrackData.get(level).graph();
            long best = Long.MIN_VALUE;
            double bestDist = Double.MAX_VALUE;
            for (var e : graph.nodes().entrySet()) {
                if (e.getValue().kind != TrackGraph.Kind.START) continue;
                double d = BlockPos.of(e.getKey()).distSqr(player.blockPosition());
                if (d < bestDist) {
                    bestDist = d;
                    best = e.getKey();
                }
            }
            if (best == Long.MIN_VALUE) {
                player.displayClientMessage(Component.translatable("message.ctrcraft.testdrive.no_track"), false);
                return 0;
            }
            BlockPos at = BlockPos.of(best);
            if (!TrackPermissions.canEdit(player, at)) {
                player.displayClientMessage(Component.translatable("message.ctrcraft.track.no_permission"), false);
                return 0;
            }
            TestDriveManager.reset(level, at);
            player.displayClientMessage(Component.translatable("message.ctrcraft.testdrive.reset"), false);
            return Command.SINGLE_SUCCESS;
        }))));
    }
}
