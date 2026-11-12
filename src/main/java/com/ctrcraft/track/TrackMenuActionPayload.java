package com.ctrcraft.track;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: a button of the track menu. The server checks everything again (the node, the distance, the permission). */
public record TrackMenuActionPayload(long start, int action) implements CustomPacketPayload {
    public static final int TOGGLE_DRIVE = 0;
    public static final int RESET = 1;
    public static final int START_RACE = 2;
    public static final int CANCEL_RACE = 3;
    public static final int CYCLE_LAPS = 4;
    private static final int[] LAP_CHOICES = { 1, 2, 3, 5, 7, 10, 20 };
    private static final double MAX_DISTANCE = 256.0;

    public static final Type<TrackMenuActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "track_menu_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackMenuActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.start);
                b.writeVarInt(p.action);
            },
            b -> new TrackMenuActionPayload(b.readLong(), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrackMenuActionPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos pos = BlockPos.of(p.start());
            TrackGraph.Node node = TrackData.get(level).graph().get(p.start());
            if (node == null || node.kind != TrackGraph.Kind.START || pos.distSqr(player.blockPosition()) > MAX_DISTANCE * MAX_DISTANCE
                    || !TrackPermissions.canEdit(player, pos)) {
                player.displayClientMessage(Component.translatable("message.ctrcraft.track.no_permission"), true);
                return;
            }
            switch (p.action()) {
                case TOGGLE_DRIVE -> {
                    if (TestDriveManager.isActive(player)) {
                        TestDriveManager.stop(player, false);
                    } else {
                        TestDriveManager.start(player, level, pos);
                    }
                }
                case RESET -> {
                    TestDriveManager.reset(level, pos);
                    player.displayClientMessage(Component.translatable("message.ctrcraft.testdrive.reset"), true);
                }
                case START_RACE -> {
                    String error = RaceManager.start(player, level, pos);
                    player.displayClientMessage(Component.translatable(error != null ? error : "message.ctrcraft.race.gathering"), true);
                }
                case CANCEL_RACE -> RaceManager.cancel(level, p.start());
                case CYCLE_LAPS -> {
                    TrackData data = TrackData.get(level);
                    TrackRoutes routes = data.routes(p.start());
                    int next = LAP_CHOICES[0];
                    for (int c : LAP_CHOICES) {
                        if (c > routes.laps()) {
                            next = c;
                            break;
                        }
                    }
                    routes.setLaps(next);
                    data.setDirty();
                    TestDriveManager.openMenu(player, level, pos);
                }
                default -> { }
            }
        });
    }
}
