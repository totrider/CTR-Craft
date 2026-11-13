package com.ctrcraft.client;

import com.ctrcraft.CtrConfig;
import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.pickup.PickupSpotBlockEntity;
import com.ctrcraft.track.GatherZone;
import com.ctrcraft.track.NodeGeometry;
import com.ctrcraft.track.RaceGrid;
import com.ctrcraft.track.TestDriveClient;
import com.ctrcraft.track.TrackClient;
import com.ctrcraft.track.TrackEvents;
import com.ctrcraft.track.TrackGraph;
import com.ctrcraft.track.TrackSnapshotPayload.NodeView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What lies on the ground round a Start node: a flat sprite for every kart spot of the grid (as many as the original game's race has, eight; the
 * server places more racers further back in the same pattern, those are not drawn) and a bigger one marking the gathering area, a little above
 * them. Shown while a track item is in hand, and for a finished track nobody is racing on. With a track item in hand it also outlines the pickup
 * spots, green where a race on the track would switch them on (within range of the track's line) and red where not.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class StartGridRenderer {
    private StartGridRenderer() {}

    private static final ResourceLocation SLOT = ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "textures/misc/grid_slot.png");
    private static final ResourceLocation ZONE = ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "textures/misc/gather_zone.png");
    /** How many spots are drawn: CTR's race has eight karts. */
    private static final int DRAWN_SLOTS = 8;
    private static final double VIEW_DISTANCE = 96.0;
    private static final double SLOT_LIFT = 0.03;
    private static final double ZONE_LIFT = 0.14;

    private static long spotsAt = Long.MIN_VALUE;
    private static final Map<BlockPos, Boolean> SPOTS = new HashMap<>();

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean holding = TrackEvents.holdsTrackItem(mc.player) || TestDriveClient.active();
        long[] idle = TrackClient.gridStarts();
        if (!holding && idle.length == 0) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose p = pose.last();

        VertexConsumer slots = buffers.getBuffer(RenderType.entityCutoutNoCull(SLOT));
        for (NodeView n : TrackClient.nodes()) {
            if (n.kind() != TrackGraph.Kind.START || !(holding || contains(idle, n.pos()))) continue;
            if (cam.distanceToSqr(NodeGeometry.x(n.pos()), NodeGeometry.y(n.pos()), NodeGeometry.z(n.pos())) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
            double y = NodeGeometry.y(n.pos());
            for (int i = 0; i < DRAWN_SLOTS; i++) {
                RaceGrid.Slot s = RaceGrid.slot(n.pos(), n.rotation(), i);
                quad(slots, p, s.x(), y + SLOT_LIFT, s.z(), n.rotation(), 1.0, 1.25);
            }
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(SLOT));

        VertexConsumer zones = buffers.getBuffer(RenderType.entityCutoutNoCull(ZONE));
        for (NodeView n : TrackClient.nodes()) {
            if (n.kind() != TrackGraph.Kind.START || !(holding || contains(idle, n.pos()))) continue;
            if (cam.distanceToSqr(NodeGeometry.x(n.pos()), NodeGeometry.y(n.pos()), NodeGeometry.z(n.pos())) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
            double[] c = GatherZone.point(n.pos(), n.rotation(), GatherZone.DEPTH / 2.0, 0.0);
            quad(zones, p, c[0], NodeGeometry.y(n.pos()) + ZONE_LIFT, c[1], n.rotation(), GatherZone.WIDTH / 2.0, GatherZone.DEPTH / 2.0);
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(ZONE));

        if (holding) outlineSpots(mc, pose, buffers);
        pose.popPose();
    }

    private static boolean contains(long[] list, long v) {
        for (long l : list) if (l == v) return true;
        return false;
    }

    /** A flat sprite centred on (cx, y, cz), {@code halfAcross} wide each side and {@code halfAlong} long each way, its top toward the node's facing. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, double cx, double y, double cz, int rotation, double halfAcross, double halfAlong) {
        double fx = NodeGeometry.facingX(rotation) * halfAlong;
        double fz = NodeGeometry.facingZ(rotation) * halfAlong;
        double lx = NodeGeometry.leftX(rotation) * halfAcross;
        double lz = NodeGeometry.leftZ(rotation) * halfAcross;
        vertex(vc, pose, cx + fx + lx, y, cz + fz + lz, 0.0F, 0.0F);
        vertex(vc, pose, cx + fx - lx, y, cz + fz - lz, 1.0F, 0.0F);
        vertex(vc, pose, cx - fx - lx, y, cz - fz - lz, 1.0F, 1.0F);
        vertex(vc, pose, cx - fx + lx, y, cz - fz + lz, 0.0F, 1.0F);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float v) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    // ---- pickup spots: green when a race on a track would switch them on, red when not ----

    private static void outlineSpots(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        long now = mc.level.getGameTime();
        if (now - spotsAt >= 20 || now < spotsAt) {
            spotsAt = now;
            refreshSpots(mc);
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Map.Entry<BlockPos, Boolean> e : SPOTS.entrySet()) {
            BlockPos b = e.getKey();
            boolean in = e.getValue();
            LevelRenderer.renderLineBox(pose, lines,
                    b.getX() - 0.02, b.getY() - 0.02, b.getZ() - 0.02, b.getX() + 1.02, b.getY() + 1.02, b.getZ() + 1.02,
                    in ? 0.2F : 1.0F, in ? 1.0F : 0.2F, 0.2F, 1.0F);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void refreshSpots(Minecraft mc) {
        SPOTS.clear();
        double radius = 96.0;
        try {
            radius = CtrConfig.PICKUP_TRACK_RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            // the server's setting is not here yet: the default
        }
        double sq = radius * radius;
        List<long[]> segments = new ArrayList<>();
        for (NodeView n : TrackClient.nodes()) {
            segments.add(new long[] { n.pos(), n.pos() });
            for (long o : n.links()) if (o > n.pos() && TrackClient.get(o) != null) segments.add(new long[] { n.pos(), o });
        }
        ChunkPos centre = mc.player.chunkPosition();
        int range = 6;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                ChunkAccess chunk = mc.level.getChunkSource().getChunk(centre.x + dx, centre.z + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (!(chunk instanceof LevelChunk lc)) continue;
                for (BlockEntity be : lc.getBlockEntities().values()) {
                    if (!(be instanceof PickupSpotBlockEntity)) continue;
                    BlockPos pos = be.getBlockPos();
                    SPOTS.put(pos, near(segments, pos, sq));
                }
            }
        }
    }

    private static boolean near(List<long[]> segments, BlockPos pos, double sq) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        for (long[] s : segments) {
            if (NodeGeometry.segmentDistSq(x, y, z, NodeGeometry.x(s[0]) + 0.5, NodeGeometry.y(s[0]) + 0.5, NodeGeometry.z(s[0]) + 0.5,
                    NodeGeometry.x(s[1]) + 0.5, NodeGeometry.y(s[1]) + 0.5, NodeGeometry.z(s[1]) + 0.5) <= sq) {
                return true;
            }
        }
        return false;
    }
}
