package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.track.NodeGeometry;
import com.ctrcraft.track.RaceClient;
import com.ctrcraft.track.RacePayload;
import com.ctrcraft.track.TrackClient;
import com.ctrcraft.track.TrackGraph;
import com.ctrcraft.track.TrackSnapshotPayload.NodeView;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The race intro, as the original does it: while the intro jingle plays the camera pans over the racers on the grid, starting at the finish line, and then comes
 * back to the driver's own kart, arriving as the jingle ends; only then do the four lamps appear. A client-only marker entity is the camera for the length of the
 * intro (the player and the kart stay where they are); the player's own camera comes back afterwards.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class RaceIntroCamera {
    private RaceIntroCamera() {}

    /** The share of the intro spent sweeping over the grid; the rest is the move back behind the own kart. */
    private static final double SWEEP = 0.68;

    private static Marker camera;
    private static Entity previous;
    private static CameraType previousType;

    private record Pose(double x, double y, double z, float yaw, float pitch) {}

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        RacePayload s = RaceClient.state();
        boolean intro = mc.player != null && mc.level != null && mc.player.getVehicle() instanceof KartEntity && s.phase() == RacePayload.LIGHTS
                && RaceClient.introMsLeft() > 0 && RaceClient.introTotalMs() > 0 && s.placeSeq() != 0;
        if (!intro) {
            if (camera != null) stop(mc);
            return;
        }
        if (camera == null) {
            camera = new Marker(EntityType.MARKER, mc.level);
            previous = mc.getCameraEntity();
            previousType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);        // the camera sits at the marker, not behind it
            mc.setCameraEntity(camera);
        }
        double t = 1.0 - (double) RaceClient.introMsLeft() / RaceClient.introTotalMs();
        Pose p = pose(s, Mth.clamp(t, 0.0, 1.0));
        camera.moveTo(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
        camera.setOldPosAndRot();
    }

    private static void stop(Minecraft mc) {
        if (mc.getCameraEntity() == camera) mc.setCameraEntity(previous != null ? previous : mc.player);
        if (previousType != null) mc.options.setCameraType(previousType);
        camera = null;
        previous = null;
        previousType = null;
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    private static Pose look(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dz = tz - z;
        return new Pose(x, y, z, (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(ty - y, Math.hypot(dx, dz))));
    }

    /** The camera at {@code t} (0 to 1) of the intro. */
    private static Pose pose(RacePayload s, double t) {
        double yawRad = Math.toRadians(s.gyaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double lx = fz;                    // to the left of the facing
        double lz = -fx;
        double sx = s.gx() + fx * 4.0;     // the finish line, taken as a little ahead of the own slot when the track's start node is not known
        double sz = s.gz() + fz * 4.0;
        double sy = s.gy();
        for (NodeView n : TrackClient.nodes()) {
            if (n.kind() != TrackGraph.Kind.START) continue;
            double nx = NodeGeometry.x(n.pos()) + 0.5;
            double nz = NodeGeometry.z(n.pos()) + 0.5;
            if (Math.hypot(nx - s.gx(), nz - s.gz()) < 40.0) {
                sx = nx;
                sz = nz;
                sy = NodeGeometry.y(n.pos());
                fx = NodeGeometry.facingX(n.rotation());
                fz = NodeGeometry.facingZ(n.rotation());
                lx = NodeGeometry.leftX(n.rotation());
                lz = NodeGeometry.leftZ(n.rotation());
                break;
            }
        }
        Pose sweep = sweep(sx, sy, sz, fx, fz, lx, lz, Math.min(t, SWEEP) / SWEEP);
        if (t <= SWEEP) return sweep;
        // the way back: behind the own kart, where the driver's camera sits
        Pose chase = new Pose(s.gx() - fx * 0.0 - Math.sin(Math.toRadians(s.gyaw())) * -4.6, s.gy() + 2.3, s.gz() - Math.cos(Math.toRadians(s.gyaw())) * 4.6,
                s.gyaw(), 14.0F);
        double k = smooth((t - SWEEP) / (1.0 - SWEEP));
        return new Pose(Mth.lerp(k, sweep.x(), chase.x()), Mth.lerp(k, sweep.y(), chase.y()), Mth.lerp(k, sweep.z(), chase.z()),
                Mth.rotLerp((float) k, sweep.yaw(), chase.yaw()), (float) Mth.lerp(k, sweep.pitch(), chase.pitch()));
    }

    /** Low in front of the line, moving from one side of the grid to the other, looking back down the rows of karts. */
    private static Pose sweep(double sx, double sy, double sz, double fx, double fz, double lx, double lz, double u) {
        double e = smooth(u);
        double across = Mth.lerp(e, 9.0, -9.0);
        double cx = sx + fx * 3.5 + lx * across;
        double cz = sz + fz * 3.5 + lz * across;
        double tAlong = Mth.lerp(e, 1.0, 11.0);                       // the point looked at moves back along the grid
        double tx = sx - fx * tAlong + lx * across * 0.35;
        double tz = sz - fz * tAlong + lz * across * 0.35;
        return look(cx, sy + 2.4, cz, tx, sy + 0.6, tz);
    }
}
