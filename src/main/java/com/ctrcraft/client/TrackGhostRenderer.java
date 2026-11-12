package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModComponents;
import com.ctrcraft.ModItems;
import com.ctrcraft.track.NodeGeometry;
import com.ctrcraft.track.TestDriveClient;
import com.ctrcraft.track.TestDrivePayload;
import com.ctrcraft.track.TrackClient;
import com.ctrcraft.track.TrackEvents;
import com.ctrcraft.track.TrackGraph;
import com.ctrcraft.track.TrackNodeBlock;
import com.ctrcraft.track.TrackNodeItem;
import com.ctrcraft.track.TrackSnapshotPayload.NodeView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the track nodes as ghosts while the player holds a track item: a box where each node is (green start, orange checkpoint, white
 * and pulsing for the selected one), an arrow for the way it faces, the ring a driver has to pass through (the size of the node's
 * radius), and a line for every link. Holding a node item also draws a PREVIEW of the node you are about to place, exactly where and
 * facing exactly as the placement will do it, with a compass of the 16 possible directions.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class TrackGhostRenderer {
    private TrackGhostRenderer() {}

    private static final double VIEW_DISTANCE = 128.0;
    /** Rotation 0 is south and it turns clockwise (west, north, east), like a standing sign. */
    private static final String[] COMPASS = { "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW", "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE" };

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(TrackEvents.holdsTrackItem(player) || TestDriveClient.active())) {
            return;
        }
        GlobalPos selected = null;
        for (ItemStack stack : new ItemStack[] { player.getMainHandItem(), player.getOffhandItem() }) {
            if (stack.is(ModItems.TRACK_TOOL.get())) {
                selected = stack.get(ModComponents.SELECTED_NODE.get());
            }
        }
        long selectedPos = selected != null && selected.dimension().equals(mc.level.dimension()) ? selected.pos().asLong() : Long.MIN_VALUE;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        float pulse = 0.55F + 0.45F * (float) Math.sin((mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.25);

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose p = pose.last();
        for (NodeView n : TrackClient.nodes()) {
            BlockPos bp = BlockPos.of(n.pos());
            double cx = bp.getX() + 0.5;
            double cy = bp.getY() + 0.5;
            double cz = bp.getZ() + 0.5;
            if (cam.distanceToSqr(cx, cy, cz) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            boolean sel = n.pos() == selectedPos;
            float[] c = sel ? new float[] { 1.0F, 1.0F, 1.0F } : colour(n.kind());
            float a = sel ? pulse : 0.9F;
            drawNode(pose, p, lines, n.pos(), n.rotation(), n.ext(), c[0], c[1], c[2], a, sel ? 0.8F : 0.4F, 1.6);
            if (TestDriveClient.isCandidate(n.pos())) {
                target(p, lines, n.pos(), n.rotation(), n.ext(), pulse);
            }

            // links, once per pair
            for (long other : n.links()) {
                if (other < n.pos() || TrackClient.get(other) == null) {
                    continue;
                }
                BlockPos ob = BlockPos.of(other);
                line(p, lines, cx, cy, cz, ob.getX() + 0.5, ob.getY() + 0.5, ob.getZ() + 0.5, 0.3F, 0.9F, 1.0F, 0.9F);
            }
        }

        // test drive: the recorded main path (gold), the branches (magenta) and the trail of the lap or branch being driven (white)
        if (TestDriveClient.active()) {
            TestDrivePayload t = TestDriveClient.state();
            chain(p, lines, t.main(), 1.0F, 0.8F, 0.1F);
            for (long[] b : t.branches()) {
                chain(p, lines, b, 1.0F, 0.3F, 0.9F);
            }
            chain(p, lines, t.trail(), 1.0F, 1.0F, 1.0F);
        }

        // the side of a box under the crosshair, with the Track Tool
        if (player.getMainHandItem().is(ModItems.TRACK_TOOL.get())) {
            BoxPicker.Pick pick = BoxPicker.pick(mc);
            NodeView picked = pick == null ? null : TrackClient.get(pick.node());
            if (picked != null) {
                face(p, lines, picked.pos(), picked.rotation(), picked.ext(), pick.side(), 1.0F, 0.9F, 0.2F, 1.0F);
                mc.gui.setOverlayMessage(Component.literal(NodeGeometry.sideName(pick.side()) + " side: " + (int) picked.ext()[pick.side()]
                        + " blocks   (right-click +1, left-click -1)"), false);
            }
        }

        // the node about to be placed
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof TrackNodeItem nodeItem && nodeItem.getBlock() instanceof TrackNodeBlock block
                && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, main, hit);
            BlockPos at = context.getClickedPos();
            int rotation = RotationSegment.convertToSegment(context.getRotation());
            double cx = at.getX() + 0.5;
            double cy = at.getY() + 0.5;
            double cz = at.getZ() + 0.5;
            if (context.canPlace()) {
                float[] c = colour(block.kind());
                drawNode(pose, p, lines, at.asLong(), rotation, NodeGeometry.DEFAULT_EXTENTS, c[0], c[1], c[2], pulse, 0.3F, 2.2);
                compass(p, lines, cx, cy, cz, rotation);
                boolean replacing = mc.level.getBlockState(at).getBlock() instanceof TrackNodeBlock;
                mc.gui.setOverlayMessage(Component.literal((replacing ? "Replace: " : "Place: ") + "facing " + COMPASS[rotation]
                        + "  (" + trim(rotation * 22.5) + " degrees, step " + rotation + " of 16)"), false);
            } else {
                LevelRenderer.renderLineBox(pose, lines, cx - 0.3, cy - 0.3, cz - 0.3, cx + 0.3, cy + 0.3, cz + 0.3, 1.0F, 0.2F, 0.2F, 0.8F);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** A line through the centres of the nodes in order, a little above them so it does not hide the node boxes. */
    private static void chain(PoseStack.Pose p, VertexConsumer lines, long[] nodes, float r, float g, float b) {
        for (int i = 1; i < nodes.length; i++) {
            BlockPos a = BlockPos.of(nodes[i - 1]);
            BlockPos c = BlockPos.of(nodes[i]);
            line(p, lines, a.getX() + 0.5, a.getY() + 0.75, a.getZ() + 0.5, c.getX() + 0.5, c.getY() + 0.75, c.getZ() + 0.5, r, g, b, 1.0F);
        }
    }

    /** A box to drive into right now: the box again, drawn twice a little inside itself, pulsing cyan, so it reads as a target. */
    private static void target(PoseStack.Pose p, VertexConsumer lines, long key, int rotation, float[] ext, float pulse) {
        box(p, lines, key, rotation, ext, 0.0, 0.2F, 1.0F, 1.0F, 0.5F + 0.5F * pulse);
        box(p, lines, key, rotation, ext, 0.15, 0.2F, 1.0F, 1.0F, 0.5F + 0.5F * pulse);
        box(p, lines, key, rotation, ext, 0.3, 0.2F, 1.0F, 1.0F, 0.5F + 0.5F * pulse);
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? Integer.toString((int) v) : Double.toString(v);
    }

    private static float[] colour(TrackGraph.Kind kind) {
        return kind == TrackGraph.Kind.START ? new float[] { 0.2F, 1.0F, 0.3F } : new float[] { 1.0F, 0.65F, 0.1F };
    }

    /** A node: a small cube on the node block, an arrow of {@code arrow} blocks for the way it faces, and its box (the area a kart has to touch). */
    private static void drawNode(PoseStack pose, PoseStack.Pose p, VertexConsumer lines, long key, int rotation, float[] ext,
                                 float r, float g, float b, float a, float boxAlpha, double arrow) {
        double cx = NodeGeometry.x(key) + 0.5;
        double cy = NodeGeometry.y(key) + 0.5;
        double cz = NodeGeometry.z(key) + 0.5;
        LevelRenderer.renderLineBox(pose, lines, cx - 0.25, cy - 0.25, cz - 0.25, cx + 0.25, cy + 0.25, cz + 0.25, r, g, b, a);
        double fx = NodeGeometry.facingX(rotation);
        double fz = NodeGeometry.facingZ(rotation);
        double lx = NodeGeometry.leftX(rotation);
        double lz = NodeGeometry.leftZ(rotation);
        double tx = cx + fx * arrow;
        double tz = cz + fz * arrow;
        line(p, lines, cx, cy, cz, tx, cy, tz, r, g, b, a);
        line(p, lines, tx, cy, tz, tx - fx * 0.4 + lx * 0.3, cy, tz - fz * 0.4 + lz * 0.3, r, g, b, a);
        line(p, lines, tx, cy, tz, tx - fx * 0.4 - lx * 0.3, cy, tz - fz * 0.4 - lz * 0.3, r, g, b, a);
        box(p, lines, key, rotation, ext, 0.0, r, g, b, boxAlpha);
    }

    /** The edges of a node's box, {@code inset} blocks inside it. */
    private static void box(PoseStack.Pose p, VertexConsumer lines, long key, int rot, float[] ext, double inset, float r, float g, float b, float a) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            double lat = (i & 1) == 0 ? ext[NodeGeometry.LEFT] - inset : -ext[NodeGeometry.RIGHT] + inset;
            double up = (i & 2) == 0 ? ext[NodeGeometry.UP] - inset : -ext[NodeGeometry.DOWN] + inset;
            double fwd = (i & 4) == 0 ? ext[NodeGeometry.FRONT] - inset : -ext[NodeGeometry.BACK] + inset;
            c[i] = NodeGeometry.world(key, rot, lat, up, fwd);
        }
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                int j = i ^ bit;
                if (j > i) {
                    line(p, lines, c[i][0], c[i][1], c[i][2], c[j][0], c[j][1], c[j][2], r, g, b, a);
                }
            }
        }
    }

    /** One side of a node's box outlined with its diagonals. */
    private static void face(PoseStack.Pose p, VertexConsumer lines, long key, int rot, float[] ext, int side, float r, float g, float b, float a) {
        double lo0 = -ext[NodeGeometry.RIGHT];
        double hi0 = ext[NodeGeometry.LEFT];
        double lo1 = -ext[NodeGeometry.DOWN];
        double hi1 = ext[NodeGeometry.UP];
        double lo2 = -ext[NodeGeometry.BACK];
        double hi2 = ext[NodeGeometry.FRONT];
        double[][] q = new double[4][];
        for (int i = 0; i < 4; i++) {
            boolean u = (i & 1) != 0;
            boolean v = (i & 2) != 0;
            double lat;
            double up;
            double fwd;
            switch (side) {
                case NodeGeometry.LEFT, NodeGeometry.RIGHT -> {
                    lat = side == NodeGeometry.LEFT ? hi0 : lo0;
                    up = u ? hi1 : lo1;
                    fwd = v ? hi2 : lo2;
                }
                case NodeGeometry.FRONT, NodeGeometry.BACK -> {
                    fwd = side == NodeGeometry.FRONT ? hi2 : lo2;
                    lat = u ? hi0 : lo0;
                    up = v ? hi1 : lo1;
                }
                default -> {
                    up = side == NodeGeometry.UP ? hi1 : lo1;
                    lat = u ? hi0 : lo0;
                    fwd = v ? hi2 : lo2;
                }
            }
            q[i] = NodeGeometry.world(key, rot, lat, up, fwd);
        }
        int[][] edges = { { 0, 1 }, { 1, 3 }, { 3, 2 }, { 2, 0 }, { 0, 3 }, { 1, 2 } };
        for (int[] e : edges) {
            line(p, lines, q[e[0]][0], q[e[0]][1], q[e[0]][2], q[e[1]][0], q[e[1]][1], q[e[1]][2], r, g, b, a);
        }
    }

    /** The 16 directions a node can face as ticks round it; the one you will get is long and white, the four main ones are brighter. */
    private static void compass(PoseStack.Pose p, VertexConsumer lines, double cx, double cy, double cz, int active) {
        for (int i = 0; i < 16; i++) {
            double yaw = Math.toRadians(i * 22.5);
            double dx = -Math.sin(yaw);
            double dz = Math.cos(yaw);
            boolean on = i == active;
            boolean main = i % 4 == 0;
            double inner = on ? 0.9 : 1.0;
            double outer = on ? 1.7 : main ? 1.35 : 1.18;
            float v = on ? 1.0F : main ? 0.85F : 0.55F;
            line(p, lines, cx + dx * inner, cy, cz + dz * inner, cx + dx * outer, cy, cz + dz * outer, v, v, on ? 1.0F : v, on ? 1.0F : 0.7F);
        }
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vc, double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4F) {
            return;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TrackClient.clear();
        TestDriveClient.clear();
        com.ctrcraft.track.RaceClient.clear();
    }
}
