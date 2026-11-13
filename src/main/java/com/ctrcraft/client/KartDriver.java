package com.ctrcraft.client;

import java.util.ArrayList;
import java.util.List;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModSounds;
import com.ctrcraft.CtrConfig;
import com.ctrcraft.kart.ContactRules;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.kart.KartFlags;
import com.ctrcraft.physics.KartEvent;
import com.ctrcraft.kart.KartScale;
import com.ctrcraft.net.KartDismountPayload;
import com.ctrcraft.net.KartHitPayload;
import com.ctrcraft.net.KartStatePayload;
import com.ctrcraft.physics.FixedMath;
import com.ctrcraft.physics.KartBump;
import com.ctrcraft.physics.KartClass;
import com.ctrcraft.physics.KartInput;
import com.ctrcraft.physics.KartSim;
import com.ctrcraft.physics.RevEngine;
import com.ctrcraft.track.RaceClient;
import com.ctrcraft.track.RacePayload;
import com.ctrcraft.terrain.Surface;
import com.ctrcraft.terrain.TerrainCollider;
import com.ctrcraft.terrain.TerrainField;
import com.ctrcraft.physics.V3;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Runs the CTR physics for the kart the local player drives. The sim ticks at CTR's fixed 30 Hz from an accumulator fed by
 * render frames (not Minecraft's 20 Hz tick); each frame the kart entity and its rider are placed at the pose interpolated
 * between the last two sim ticks. The server only validates (vanilla's move-vehicle packet); other players see the kart
 * through normal entity tracking.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class KartDriver {
    private KartDriver() {}

    private static KartSim sim;
    private static TerrainCollider world;
    private static int simEntityId = -1;
    private static long lastNanos;
    private static double accumulator;
    private static boolean latchR1;
    private static boolean latchL1;
    private static boolean firstRideFrame;
    /** The engine revving on the race grid (null when not held there). */
    private static RevEngine rev;

    // the last two sim poses, for interpolation
    private static V3 prevPos = V3.ZERO;
    private static V3 curPos = V3.ZERO;
    private static int prevHeading;
    private static int curHeading;
    private static int prevDrawn;
    private static int curDrawn;

    private static void reset() {
        com.ctrcraft.pickup.ItemClient.clear();
        sim = null;
        simEntityId = -1;
        accumulator = 0;
        latchR1 = false;
        latchL1 = false;
        rev = null;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null
                || !(player.getVehicle() instanceof KartEntity kart) || kart.getControllingPassenger() != player) {
            reset();
            return;
        }
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1.0e9, 0.1);
        lastNanos = now;
        if (mc.isPaused()) {
            return;
        }

        if (sim == null || simEntityId != kart.getId()) {
            sim = new KartSim(KartClass.BALANCED, toLu(kart.position()), KartScale.yawToAngle(kart.getYRot()));
            simEntityId = kart.getId();
            accumulator = 0;
            snapshot(true);
            firstRideFrame = true;
        }
        if (firstRideFrame) {
            firstRideFrame = false;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        accumulator = Math.min(accumulator + dt, 0.25);
        Options o = mc.options;
        boolean r1 = o.keyJump.isDown();
        boolean l1 = CtrCraftClient.KEY_HOP2.isDown();
        latchR1 |= r1;
        latchL1 |= l1;
        if (world == null || worldLevel != mc.level) {
            world = new TerrainCollider(new TerrainField(new McBlockSource(mc.level, kart)), KartScale.LU_PER_BLOCK);
            worldLevel = mc.level;
        }
        // the server put us on the race grid: stand there
        RacePayload slot = RaceClient.takePlacement();
        if (slot != null) {
            sim.place(new V3(KartScale.toLu(slot.gx()), KartScale.toLu(slot.gy()), KartScale.toLu(slot.gz())), KartScale.yawToAngle(slot.gyaw()));
            snapshot(true);
            accumulator = 0;
            rev = null;
        }
        while (accumulator >= KartSim.TICK_SECONDS) {
            accumulator -= KartSim.TICK_SECONDS;
            double steer = (o.keyLeft.isDown() ? -1.0 : 0.0) + (o.keyRight.isDown() ? 1.0 : 0.0);
            KartInput input = new KartInput(steer, o.keyUp.isDown(), CtrCraftClient.KEY_BRAKE.isDown(),
                    o.keyDown.isDown(), latchR1, latchL1);
            // Held on the grid until GO (VehStuckProc_RevEngine): the gas revs the engine instead of driving; at GO the revs earn the start boost
            if (RaceClient.hold()) {
                if (rev == null) {
                    rev = new RevEngine(sim.accelSpeed());
                }
                if (rev.tick(o.keyUp.isDown(), KartSim.MS)) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BOOST_FAIL.get(), 1.0F, 1.0F));
                }
                input = new KartInput(0.0, false, true, false, false, false);
            } else if (rev != null) {
                RevEngine.StartBoost boost = rev.release();
                rev = null;
                if (boost != null) {
                    sim.startBoost(boost);
                }
            }
            latchR1 = false;
            latchL1 = false;
            world.prepare(sim.position(), sim.velocity().y / 256.0);
            int surface = world.surfaceAt(sim.position());
            if (surface >= 0) {
                sim.groundType = Surface.terrain(surface);
                if (Surface.isPad(surface) && sim.grounded()) {
                    sim.turboPad();
                }
            }
            if (sim.cloudActive() && mc.level != null) {
                // the rain cloud over the kart (the original's is a 3D cloud model with a rain emitter; these particles stand in for it)
                for (int i = 0; i < 3; i++) {
                    double ox = (mc.level.random.nextDouble() - 0.5) * 1.6;
                    double oz = (mc.level.random.nextDouble() - 0.5) * 1.6;
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, kart.getX() + ox, kart.getY() + 2.4, kart.getZ() + oz, 0.0, 0.0, 0.0);
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.RAIN, kart.getX() + ox, kart.getY() + 1.6, kart.getZ() + oz, 0.0, 0.0, 0.0);
                }
            }
            if (sim.cloudIce()) sim.groundType = com.ctrcraft.physics.Terrain.ICE;          // RAIN_CLOUD_EFFECT_ICE_TERRAIN
            sim.setOthers(nearbyKarts(mc, kart));
            sim.wumpa = com.ctrcraft.pickup.ItemClient.wumpa();
            int itemEffect = com.ctrcraft.pickup.ItemClient.takeEffect();
            if (itemEffect == com.ctrcraft.pickup.ItemManager.EFFECT_TURBO || itemEffect == com.ctrcraft.pickup.ItemManager.EFFECT_TURBO_JUICED) {
                sim.itemTurbo(itemEffect == com.ctrcraft.pickup.ItemManager.EFFECT_TURBO_JUICED);
            } else if (itemEffect == com.ctrcraft.pickup.ItemManager.EFFECT_SLOW) {
                tntUntilMs = System.currentTimeMillis() + (com.ctrcraft.pickup.TntEntity.FUSE_TICKS + 12) * 50L;
            } else if (itemEffect == com.ctrcraft.pickup.ItemManager.EFFECT_SPIN) {
                sim.itemSpin();
                ScreenFlash.start();
            } else if (itemEffect >= com.ctrcraft.pickup.ItemManager.EFFECT_CLOUD) {
                sim.setCloud(itemEffect - com.ctrcraft.pickup.ItemManager.EFFECT_CLOUD, com.ctrcraft.pickup.ItemManager.CLOUD_TICKS * 50);
            } else if (itemEffect == com.ctrcraft.pickup.ItemManager.EFFECT_CLEAR) {
                tntUntilMs = 0;
            } else if (itemEffect == com.ctrcraft.pickup.ItemManager.EFFECT_BLAST) {
                sim.itemBlast();
            }
            sim.tick(world, input);
            sendHits(mc, kart, sim.drainBumps());
            playEvents(mc, sim.drainEvents());
            snapshot(false);
        }
        place(mc, player, kart, accumulator / KartSim.TICK_SECONDS, dt);

        // Sneak dismounts: tell the server where the kart really is first (see KartDismountPayload).
        if (mc.options.keyShift.isDown()) {
            if (!dismountSent) {
                dismountSent = true;
                PacketDistributor.sendToServer(new KartDismountPayload(kart.getId(), kart.getX(), kart.getY(), kart.getZ()));
            }
        } else {
            dismountSent = false;
        }
    }

    /**
     * The other karts within bumping range (VehPhysForce_CollideDrivers' thread search): their position in lu and the velocity they
     * moved with over the last game tick, in lu/256 per sim frame.
     */
    private static List<KartBump.Other> nearbyKarts(Minecraft mc, KartEntity self) {
        double reach = KartScale.toBlocks(2.0 * KartBump.HIT_RADIUS) + 1.0;
        boolean entities = contactOn();
        List<Entity> near = mc.level.getEntitiesOfClass(Entity.class, self.getBoundingBox().inflate(reach),
                e -> e != self && !e.isRemoved() && !e.isSpectator() && !self.hasPassenger(e) && (e instanceof KartEntity || (entities && bumpable(e))));
        if (near.isEmpty()) {
            return List.of();
        }
        double perFrame = KartScale.LU_PER_BLOCK * 20.0 / 30.0 * 256.0; // blocks per game tick -> lu/256 per sim frame
        List<KartBump.Other> out = new ArrayList<>(near.size());
        for (Entity k : near) {
            V3 v = new V3((k.getX() - k.xo) * perFrame, (k.getY() - k.yo) * perFrame, (k.getZ() - k.zo) * perFrame);
            if (k instanceof KartEntity) {
                out.add(new KartBump.Other(toLu(k.position()), v, KartBump.weight(KartClass.BALANCED)));
            } else {
                out.add(new KartBump.Other(toLu(k.position()), v, ContactRules.weight(k.getBbWidth(), k.getBbHeight()),
                        ContactRules.reach(k.getBbWidth()), k.getId()));
            }
        }
        return out;
    }

    /** What a kart runs into besides other karts: living things (mobs, players on foot) and vehicles (boats, minecarts) that are not riding. */
    private static boolean bumpable(Entity e) {
        return (e instanceof LivingEntity && !(e instanceof ArmorStand) || e instanceof VehicleEntity) && e.getVehicle() == null;
    }

    private static boolean contactOn() {
        try {
            return CtrConfig.ENTITY_CONTACT.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /** The server throws and hurts what this kart hit at speed (kart against kart has no damage, as in CTR). */
    private static void sendHits(Minecraft mc, KartEntity kart, List<KartSim.Bump> bumps) {
        long now = System.currentTimeMillis();
        for (KartSim.Bump b : bumps) {
            if (b.id() < 0 || b.impact() < KartBump.FEEDBACK_MIN_IMPACT) {
                continue;
            }
            Long last = LAST_HIT_SENT.get(b.id());
            if (last != null && now - last < ContactRules.HIT_COOLDOWN_TICKS * 50L) {
                continue;
            }
            LAST_HIT_SENT.put(b.id(), now);
            if (LAST_HIT_SENT.size() > 256) {
                LAST_HIT_SENT.values().removeIf(t -> now - t > 5000);
            }
            // the normal runs from the entity to the kart; the entity is hit the opposite way
            PacketDistributor.sendToServer(new KartHitPayload(kart.getId(), b.id(), b.impact(), (float) -b.normal().x, (float) -b.normal().z));
        }
    }

    private static final java.util.Map<Integer, Long> LAST_HIT_SENT = new java.util.HashMap<>();

    private static boolean dismountSent;
    private static net.minecraft.world.level.Level worldLevel;

    /** Pushes the sim state to the previous/current slots. */
    private static void snapshot(boolean both) {
        if (!both) {
            prevPos = curPos;
            prevHeading = curHeading;
            prevDrawn = curDrawn;
        }
        curPos = sim.position();
        curHeading = sim.heading();
        curDrawn = sim.drawnYaw();
        if (both) {
            prevPos = curPos;
            prevHeading = curHeading;
            prevDrawn = curDrawn;
        }
    }

    private static void place(Minecraft mc, LocalPlayer player, KartEntity kart, double alpha, double dt) {
        V3 p = new V3(Mth.lerp(alpha, prevPos.x, curPos.x), Mth.lerp(alpha, prevPos.y, curPos.y),
                Mth.lerp(alpha, prevPos.z, curPos.z));
        double x = KartScale.toBlocks(p.x);
        double y = KartScale.toBlocks(p.y);
        double z = KartScale.toBlocks(p.z);
        int heading = FixedMath.wrap(prevHeading + (int) Math.round(alpha * FixedMath.diff(curHeading, prevHeading)));
        int drawn = FixedMath.wrap(prevDrawn + (int) Math.round(alpha * FixedMath.diff(curDrawn, prevDrawn)));
        float yaw = KartScale.angleToYaw(heading);

        kart.clearLerp();
        kart.setPos(x, y, z);
        kart.xo = x;
        kart.yo = y;
        kart.zo = z;
        kart.xOld = x;
        kart.yOld = y;
        kart.zOld = z;
        kart.setYRot(yaw);
        kart.yRotO = yaw;
        V3 v = sim.velocity();
        double perTick = 30.0 / 20.0 / KartScale.LU_PER_BLOCK / 256.0; // lu/256 per sim frame -> blocks per game tick
        kart.setDeltaMovement(v.x * perTick, v.y * perTick, v.z * perTick);
        kart.setOnGround(sim.grounded());
        kart.setDrawnYawAngle(drawn);
        kart.setVisualFlags(visualFlags());
        kart.setEngineData(engineData());
        kart.setTilt(tiltData());

        // the rider sits where the kart says, every frame (vanilla only does it once a tick)
        Vec3 seat = kart.getPassengerRidingPosition(player);
        Vec3 attach = player.getVehicleAttachmentPoint(kart);
        double px = seat.x - attach.x;
        double py = seat.y - attach.y;
        double pz = seat.z - attach.z;
        player.setPos(px, py, pz);
        player.xo = px;
        player.yo = py;
        player.zo = pz;
        player.xOld = px;
        player.yOld = py;
        player.zOld = pz;

        // chase camera: the view swings round behind the kart (Mario Kart style), never against the mouse for long
        float follow = (float) (1.0 - Math.exp(-dt * 7.0));
        float camYaw = Mth.rotLerp(follow, player.getYRot(), KartScale.angleToYaw(sim.drawnYaw()));
        player.setYRot(camYaw);
        player.yRotO = camYaw;
        player.setXRot(Mth.lerp(follow, player.getXRot(), 12.0F));
        player.xRotO = player.getXRot();
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private static int visualFlags() {
        return KartFlags.encode(sim);
    }

    private static int engineData() {
        int[] e = sim.engineSound();
        return (e[0] & 0xFF) | ((e[1] & 0xFF) << 8);
    }

    /** The kart's smoothed ground normal (what it leans on) as two bytes: x and z, value = n * 127 + 127. */
    private static int tiltData() {
        com.ctrcraft.physics.V3 up = sim.facing().y;
        int nx = (int) Math.round(Math.max(-1.0, Math.min(1.0, up.x)) * 127.0) + 127;
        int nz = (int) Math.round(Math.max(-1.0, Math.min(1.0, up.z)) * 127.0) + 127;
        return (nx & 0xFF) | ((nz & 0xFF) << 8);
    }

    /** The grid rev the HUD shows, or null when not revving. */
    public static RevEngine rev() {
        return rev;
    }

    /** The sim the HUD reads, or null when the local player is not driving. */
    public static KartSim activeSim() {
        return sim;
    }

    /** One-shot sounds for what just happened, played at the listener (it is your own kart). */
    /** While a TNT crate is on or flying onto the kart: every jump that starts is reported (the server counts them). */
    private static long tntUntilMs;

    private static void playEvents(Minecraft mc, List<KartEvent> events) {
        for (KartEvent e : events) {
            if (e.type() == KartEvent.Type.HOP && System.currentTimeMillis() < tntUntilMs) {
                PacketDistributor.sendToServer(new com.ctrcraft.pickup.TntShakePayload());       // the server counts the jumps, as the game does
            }
            SoundEvent sound = null;
            float pitch = 1.0F;
            float volume = 1.0F;
            switch (e.type()) {
                case HOP -> sound = ModSounds.HOP.get();
                case LAND -> {
                    if (e.value() >= 160) {
                        sound = ModSounds.LAND.get();
                        volume = Math.min(1.0F, 0.35F + e.value() / 900.0F);
                    }
                }
                case DRIFT_START -> sound = ModSounds.DRIFT_START.get();
                case BOOST -> {
                    // VehFire_Audio: boost 1 plays higher and quiet (distortion 0x94, volume 0x80), boost 2 plain (0xC0),
                    // boost 3 lower and full (0x6C, 0xFF); the pitch factor is 2^((d - 0x80) / 128)
                    sound = ModSounds.BOOST.get();
                    switch (e.value()) {
                        case 1 -> { pitch = 1.114F; volume = 0x80 / 255.0F + 0.15F; }
                        case 2 -> { pitch = 1.0F; volume = 0xC0 / 255.0F + 0.1F; }
                        default -> { pitch = 0.897F; volume = 1.0F; }
                    }
                }
                case BOOST_EARLY -> sound = ModSounds.BOOST_FAIL.get();
                case METER_RED -> sound = ModSounds.METER_RED.get();
                case METER_FULL -> sound = ModSounds.METER_FULL.get();
                case HANG_TIME -> {
                    sound = ModSounds.HANG_TIME.get();
                    pitch = 1.0F + 0.15F * (e.value() - 1);
                }
                case WALL_RUB -> sound = ModSounds.WALL_HIT.get();
                case SLAM -> sound = ModSounds.SLAM.get();
                // OtherFX_DriverCrashing: crash 0xA from volume 0xDD up, 0xC above 0xA0, else 0xB; the volume is VehPhysCrash's
                case BUMP -> {
                    sound = e.value() >= 0xDD ? ModSounds.CRASH_HARD.get() : e.value() > 0xA0 ? ModSounds.CRASH_MID.get() : ModSounds.CRASH_SOFT.get();
                    volume = e.value() / 255.0F;
                }
                case SPIN_OUT -> sound = ModSounds.SPIN_OUT.get();
                default -> { }
            }
            if (sound != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }
    }

    private static final int LEAVE_TICKS = 40;
    private static int leaveHeld;

    private static V3 toLu(Vec3 pos) {
        return new V3(KartScale.toLu(pos.x), KartScale.toLu(pos.y), KartScale.toLu(pos.z));
    }

    /** Once a game tick, tell the server where the kart is and how it looks (replaces vanilla's move-vehicle packet). */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (CtrCraftClient.KEY_ITEM.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() instanceof KartEntity) {
                PacketDistributor.sendToServer(new com.ctrcraft.pickup.UseItemPayload(mc.options.keyUp.isDown()));
            }
        }
        // leaving a race: the key held for two seconds
        if (RaceClient.active() && mc.player != null && mc.player.getVehicle() instanceof KartEntity && CtrCraftClient.KEY_LEAVE.isDown()) {
            leaveHeld++;
            if (leaveHeld >= LEAVE_TICKS) {
                leaveHeld = 0;
                PacketDistributor.sendToServer(new com.ctrcraft.track.LeaveRacePayload());
            } else if (leaveHeld % 10 == 1) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.ctrcraft.race.leaving", leaveHeld * 100 / LEAVE_TICKS), true);
            }
        } else {
            leaveHeld = 0;
        }
        while (CtrCraftClient.KEY_RESPAWN.consumeClick()) {
            if (RaceClient.active()) {
                PacketDistributor.sendToServer(new com.ctrcraft.track.RespawnPayload());
            }
        }
        if (sim != null && mc.player != null && mc.player.getVehicle() instanceof KartEntity kart
                && kart.getId() == simEntityId && !mc.isPaused()) {
            PacketDistributor.sendToServer(new KartStatePayload(kart.getId(), kart.getX(), kart.getY(), kart.getZ(),
                    kart.getYRot(), sim.drawnYaw(), visualFlags(), engineData(), tiltData()));
        }
    }
}
