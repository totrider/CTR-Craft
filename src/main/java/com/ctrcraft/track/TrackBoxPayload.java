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

/**
 * Client to server: move one side of a node's box by one block (the Track Tool: right-click a side to extend it, left-click to retract it). The server checks the node, the distance and the permission, then changes the box.
 */
public record TrackBoxPayload(long node, int side, int delta) implements CustomPacketPayload {
    /** How far from the node the player may be: the longest pick range of the client. */
    public static final double MAX_DISTANCE = 160.0;

    public static final Type<TrackBoxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "track_box"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackBoxPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.node);
                b.writeVarInt(p.side);
                b.writeVarInt(p.delta);
            },
            b -> new TrackBoxPayload(b.readLong(), b.readVarInt(), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrackBoxPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos pos = BlockPos.of(p.node());
            if (!player.getMainHandItem().is(com.ctrcraft.ModItems.TRACK_TOOL.get()) || pos.distSqr(player.blockPosition()) > MAX_DISTANCE * MAX_DISTANCE) return;
            if (!TrackPermissions.canEdit(player, pos)) {
                player.displayClientMessage(Component.translatable("message.ctrcraft.track.no_permission"), true);
                return;
            }
            TrackData data = TrackData.get(level);
            float v = data.graph().resize(p.node(), p.side(), Integer.signum(p.delta()));
            if (v < 0) return;
            data.setDirty();
            TrackGraph.Node n = data.graph().get(p.node());
            float width = n.ext[NodeGeometry.LEFT] + n.ext[NodeGeometry.RIGHT];
            float depth = n.ext[NodeGeometry.FRONT] + n.ext[NodeGeometry.BACK];
            float height = n.ext[NodeGeometry.UP] + n.ext[NodeGeometry.DOWN];
            player.displayClientMessage(Component.translatable("message.ctrcraft.track.box", NodeGeometry.sideName(p.side()), (int) v,
                    (int) width, (int) depth, (int) height), true);
        });
    }
}
