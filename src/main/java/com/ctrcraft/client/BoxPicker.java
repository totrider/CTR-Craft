package com.ctrcraft.client;

import com.ctrcraft.track.NodeGeometry;
import com.ctrcraft.track.TrackClient;
import com.ctrcraft.track.TrackNodeBlock;
import com.ctrcraft.track.TrackSnapshotPayload.NodeView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Which side of which node's box the player is pointing at with the Track Tool: a long ray (the boxes are big, and you may stand in the
 * middle of one) against the boxes of the nodes the server sent. The node itself and any block nearer than the box side win, so linking
 * a node and building next to a box still work as usual.
 */
public final class BoxPicker {
    private BoxPicker() {}

    public static final double RANGE = 128.0;

    public record Pick(long node, int side, double distance) {}

    public static Pick pick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Pick best = null;
        for (NodeView n : TrackClient.nodes()) {
            double cx = NodeGeometry.x(n.pos()) + 0.5;
            double cy = NodeGeometry.y(n.pos()) + 0.5;
            double cz = NodeGeometry.z(n.pos()) + 0.5;
            if (eye.distanceToSqr(cx, cy, cz) > (RANGE + 90) * (RANGE + 90)) continue;
            NodeGeometry.FaceHit hit = NodeGeometry.rayFace(n.pos(), n.rotation(), n.ext(), eye.x, eye.y, eye.z, look.x, look.y, look.z, RANGE);
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = new Pick(n.pos(), hit.side(), hit.distance());
            }
        }
        if (best == null) return null;
        // the node (to link it) and any nearer block (to build or break) come first
        if (mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
            if (mc.level.getBlockState(block.getBlockPos()).getBlock() instanceof TrackNodeBlock) return null;
            if (eye.distanceTo(block.getLocation()) < best.distance()) return null;
        }
        return best;
    }
}
