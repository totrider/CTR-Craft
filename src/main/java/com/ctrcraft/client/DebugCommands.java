package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client side debug commands (they only change what this client draws, so they need no operator rights and work in multiplayer):
 * {@code /ctrdebug flame <size 1-8> [ultra]} shows the boost flame on every kart whatever it is doing, {@code /ctrdebug flame off} gives the flame back to the game; {@code flipx}, {@code flipz} and {@code bright <0.05-1>} are trial settings.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class DebugCommands {
    private DebugCommands() {}

    /** The flame size forced on every kart, 0 for none (the game decides). */
    public static volatile int flameSize = 0;
    public static volatile boolean flameUltra = false;
    /** Trial settings for the flame's placement, while its orientation is being found: turn it about the x or the z axis of the pipe (a mirror), and its brightness (0.1 to 1). */
    public static volatile boolean flameFlipX = false;
    public static volatile boolean flameFlipZ = true;
    /** The model turned half a revolution about its x axis (y and z negated): what lines the original's flame model up with the exhaust (found in a model viewer). */
    public static volatile boolean flameRotX = true;
    public static volatile float flameBright = 1.0F;
    /** Trial nudge of the flame's root from the pipe's tip, blocks: out to the side (away from the middle), up, back. */
    public static volatile float flameDx = 0.0F;
    public static volatile float flameDy = 0.0F;
    public static volatile float flameDz = 0.0F;

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ctrdebug").then(Commands.literal("flame")
                .then(Commands.literal("off").executes(ctx -> {
                    flameSize = 0;
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame: left to the game"), false);
                    return 1;
                }))
                .then(Commands.literal("flipx").executes(ctx -> {
                    flameFlipX = !flameFlipX;
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame mirrored in x: " + flameFlipX), false);
                    return 1;
                }))
                .then(Commands.literal("rotx").executes(ctx -> {
                    flameRotX = !flameRotX;
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame turned 180 degrees about x: " + flameRotX), false);
                    return 1;
                }))
                .then(Commands.literal("flipz").executes(ctx -> {
                    flameFlipZ = !flameFlipZ;
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame mirrored in z (front/back): " + flameFlipZ), false);
                    return 1;
                }))
                .then(Commands.literal("nudge").then(Commands.argument("out", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-2.0F, 2.0F))
                        .then(Commands.argument("up", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-2.0F, 2.0F))
                                .then(Commands.argument("back", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-2.0F, 2.0F)).executes(ctx -> {
                                    flameDx = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "out");
                                    flameDy = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "up");
                                    flameDz = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "back");
                                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame nudged (blocks): out " + flameDx + ", up " + flameDy + ", back " + flameDz), false);
                                    return 1;
                                })))))
                .then(Commands.literal("bright").then(Commands.argument("value", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.05F, 1.0F)).executes(ctx -> {
                    flameBright = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "value");
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame brightness: " + flameBright), false);
                    return 1;
                })))
                .then(Commands.argument("size", IntegerArgumentType.integer(1, 8)).executes(ctx -> set(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size"), false))
                        .then(Commands.literal("ultra").executes(ctx -> set(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size"), true))))));
    }

    private static int set(net.minecraft.commands.CommandSourceStack source, int size, boolean ultra) {
        flameSize = size;
        flameUltra = ultra;
        source.sendSuccess(() -> Component.literal("Boost flame forced on every kart: size " + size + (ultra ? ", ultra" : "") + " (/ctrdebug flame off to stop)"), false);
        return 1;
    }
}
