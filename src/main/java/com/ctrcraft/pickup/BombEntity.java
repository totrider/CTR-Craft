package com.ctrcraft.pickup;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.ctrcraft.CtrConfig;
import com.ctrcraft.ModParticles;
import com.ctrcraft.ModSounds;
import com.ctrcraft.client.McBlockSource;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.terrain.TerrainField;
import com.ctrcraft.track.RaceManager;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Bomb power-up (docs/POWERUPS.md): a bomb that rolls along the ground, in the kart's heading (backwards at 0.3 of the speed when the back key is held), steering
 * slowly towards the kart it was aimed at. Port of {@code RB_MovingExplosive_ThTick} / {@code RB_MovingExplosive_Explode} / {@code RB_Burst_Init} (ctr-native
 * game/231), launched as in {@code VehPickupItem_ShootNow} (WEAPON_ID_BOMB_MISSILE). Speeds are the game's in lu per frame of 30 Hz, 50 lu to the block, a game tick
 * being 1.5 frames; the movement is done in half-frame steps so a fast bomb cannot skip over a kart.
 * <ul>
 * <li>It explodes on a wall (it climbs a block like a kart does), when it has fallen 0x900 lu (46 blocks) without finding ground, on any kart other than the
 * owner's whose centre comes within 96 lu (sqrt 0x2400) horizontally, on a mine, or when the owner presses the use key again ({@code tw->flags |= 2}).</li>
 * <li>The owner is spared for 60 frames; after that the bomb explodes on the owner's kart too, but the burst never hurts the owner.</li>
 * <li>The burst blasts every other driven kart within 320 lu (6.4 blocks), 512 lu (10.24 blocks) when the owner had ten wumpa: {@link ItemManager#EFFECT_BLAST}
 * and the wumpa penalty. Mines in the blast go off too. Other entities are hurt and thrown by a vanilla explosion of the same reach, without breaking blocks.</li>
 * </ul>
 * <b>Stand-ins, not in the decomp:</b> the target is the nearest other driven kart in a 45 degree cone ahead of the launching kart (the original tests the screen
 * rectangle, which a mod cannot know), and a bomb goes away after 20 s ({@link #LIFETIME_TICKS}) so a bomb cannot roll for ever; the original has no time limit.
 */
public class BombEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_POWERED = SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BACKWARD = SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.BOOLEAN);

    /** TRACKER_LAUNCH_VELOCITY: 4096 * 3 >> 7 = 96 lu per frame; backwards (v / 2) * 3 / 5. */
    private static final double SPEED = 96.0;
    private static final double SPEED_BACKWARD = 96.0 * 0.3;
    /** Gravity (elapsed << 2) >> 5 = 4 lu per frame per frame, terminal -0x60. */
    private static final double GRAVITY = 4.0;
    private static final double TERMINAL = -96.0;
    private static final double FRAMES_PER_TICK = 1.5;
    private static final double LU = com.ctrcraft.kart.KartScale.LU_PER_BLOCK;
    /** Small steps (0.29 block at full speed): a stair or slope is climbed one riser at a time (the one-block step-up only copes with one rise per move), and a kart cannot be skipped. */
    private static final int SUBSTEPS = 10;
    private static final double SUB = FRAMES_PER_TICK / SUBSTEPS;
    /** RB_Hazard_InterpolateValue(.., 4): 4 of 4096 per frame. */
    private static final double TURN_PER_FRAME = 4.0 * Math.PI * 2.0 / 4096.0;
    /** The hit test of RB_Hazard_CollideWithDrivers: 0x2400 squared radius, horizontally; the vertical bound is twice the radius (dy^2 < 0x2400 * 4). */
    private static final double HIT_RADIUS = 96.0 / LU;
    private static final double HIT_HEIGHT = 192.0 / LU;
    /** RB_Burst_Init: 0x140 lu, or 0x200 lu when TRACKER_FLAG_POWERED_UP. */
    private static final double BLAST_RADIUS = 320.0 / LU;
    private static final double BLAST_RADIUS_POWERED = 512.0 / LU;
    /** TRACKER_PARENT_SAFETY_FRAMES 60, in game ticks. */
    private static final int OWNER_SPARED_TICKS = 40;
    /** No ground for 0x900 lu below: it explodes. */
    private static final double MAX_FALL = 0x900 / LU;
    private static final int LIFETIME_TICKS = 400;
    /** ACTIVE_MISSILE_LIMIT: no more than twelve bombs and missiles at once. */
    public static final int ACTIVE_LIMIT = 12;
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    /** The stand-in for the screen test: half the angle of the cone ahead of the launcher that a target must be in, and the farthest target, in blocks. */
    private static final double TARGET_HALF_CONE = Math.toRadians(45.0);
    private static final double TARGET_RANGE = 80.0;

    private UUID owner;
    private UUID target;
    private String raceId;
    private double heading;
    private double vy;
    private boolean counted;
    private boolean detonate;
    /** On the ground (the step down found something under it last step). */
    private boolean grounded;
    private boolean soundStarted;
    /** The farthest it looks down for ground to keep following a slope: a stair or a slab edge, as a kart's wheels do (the rest is a fall). */
    private static final double STEP_DOWN = 1.0;
    /** Half the width of the column in front of the bomb that is tested for a wall (the ball is about 64 lu across). */
    private static final double WALL_HALF = 0.3;
    private TerrainField field;
    private double prepX;
    private double prepY;
    private double prepZ;
    private double fell;

    public BombEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Whether another bomb or missile may be launched (ACTIVE_MISSILE_LIMIT). */
    public static boolean canLaunch() {
        return ACTIVE.get() < ACTIVE_LIMIT;
    }

    public boolean powered() {
        return entityData.get(DATA_POWERED);
    }

    public boolean backward() {
        return entityData.get(DATA_BACKWARD);
    }

    /** Heading in radians, as the renderer turns the bomb; the bomb travels along (-sin, cos). */
    public double heading() {
        return Math.toRadians(getYRot());          // the server keeps the same angle in yRot, which is what reaches the clients
    }

    /** Starts the bomb at the launching kart ({@code kart} faces the way it goes; the target is chosen here, as VehPickupItem_MissileGetTargetDriver). */
    public void launch(ServerPlayer driver, KartEntity kart, boolean powered, boolean backward) {
        owner = driver.getUUID();
        raceId = RaceManager.raceIdOf(owner);
        heading = Math.toRadians(kart.getYRot());
        entityData.set(DATA_POWERED, powered);
        entityData.set(DATA_BACKWARD, backward);
        setYRot(kart.getYRot());
        double side = backward ? -1.6 : 1.2;
        setPos(kart.getX() - Math.sin(heading) * side, kart.getY(), kart.getZ() + Math.cos(heading) * side);
        grounded = true;                          // it starts on the ground the kart is on: the first step puts it on the surface
        target = chooseTarget(driver, kart);
        ACTIVE.incrementAndGet();
        counted = true;
    }

    private UUID chooseTarget(ServerPlayer driver, KartEntity from) {
        ServerPlayer best = null;
        double bestDist = TARGET_RANGE * TARGET_RANGE;
        for (ServerPlayer p : ((ServerLevel) level()).getServer().getPlayerList().getPlayers()) {
            if (p == driver || !(p.getVehicle() instanceof KartEntity k) || k.level() != level()) continue;
            double dx = k.getX() - from.getX();
            double dz = k.getZ() - from.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 >= bestDist) continue;
            double away = Math.atan2(-dx, dz);
            if (Math.abs(wrap(away - heading)) > TARGET_HALF_CONE) continue;
            best = p;
            bestDist = d2;
        }
        return best == null ? null : best.getUUID();
    }

    /** The owner pressed the use key again (tw->flags |= 2). */
    public void detonate() {
        detonate = true;
    }

    public UUID owner() {
        return owner;
    }

    private static double wrap(double a) {
        a = (a + Math.PI) % (Math.PI * 2.0);
        if (a < 0) a += Math.PI * 2.0;
        return a - Math.PI;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_POWERED, false);
        builder.define(DATA_BACKWARD, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!soundStarted) {
                soundStarted = true;
                com.ctrcraft.client.BombRollSound.start(this);               // 0x48, looped while it rolls
            }
            com.ctrcraft.client.BombFx.tick(this);                           // the little fire off the fuse's tip
            return;
        }
        if (!(level() instanceof ServerLevel level)) return;
        if (detonate || tickCount > LIFETIME_TICKS || (raceId != null && !raceId.equals(RaceManager.raceIdOf(owner)))) {
            if (detonate) explode(level, "the use key"); else vanish();
            return;
        }
        // RB_MovingExplosive_ThTick: steer towards the target (a driver that is gone or unseated is no target any more)
        ServerPlayer chased = target == null ? null : level.getServer().getPlayerList().getPlayer(target);
        if (chased != null && !(chased.getVehicle() instanceof KartEntity)) chased = null;
        if (chased == null) target = null;
        double speed = (backward() ? SPEED_BACKWARD : SPEED) * com.ctrcraft.CtrConfig.WEAPON_SPEED_SCALE.get();
        for (int i = 0; i < SUBSTEPS; i++) {
            if (chased != null) {
                double dx = chased.getX() - getX();
                double dz = chased.getZ() - getZ();
                double diff = wrap(Math.atan2(-dx, dz) - heading);
                double step = TURN_PER_FRAME * SUB;
                heading += Math.abs(diff) < TURN_PER_FRAME ? diff : Math.copySign(step, diff);
            }
            // a bomb thrown backwards (TRACKER_FLAG_BOMB_BACKWARD) travels the OPPOSITE way along its heading, at 0.3 of the speed: its velocity is negated (RB_MovingExplosive_ThTick)
            double step = speed * SUB / LU * (backward() ? -1.0 : 1.0);
            double nx = getX() - Math.sin(heading) * step;
            double nz = getZ() + Math.cos(heading) * step;
            double y = getY();
            // the terrain window is rebuilt when the bomb has moved a block and a half or changed height (the field only knows ground up to a step above where it was prepared)
            if (field == null || Math.abs(y - prepY) > 0.4 || (nx - prepX) * (nx - prepX) + (nz - prepZ) * (nz - prepZ) > 2.25) {
                if (field == null) field = new TerrainField(new McBlockSource(level, this));
                field.prepare(getX(), y + TerrainField.RIDE_LAG, getZ(), 0.0);          // around the real height the bomb is over, not the smoothed one under it
                prepX = getX();
                prepY = y;
                prepZ = getZ();
            }
            // the original puts the bomb on the ground under it every frame (the quad block hit + 0x30) and only a wall explodes it. The ground is the kart's own: the terrain
            // field's cone surface, so a full block, a stair or a 45 degree wedge is a ramp it rolls up and down; where the ground drops away by more than a block it is in the air
            double ground = field.surface(nx, nz);
            if (grounded && !Double.isNaN(ground) && ground >= y - STEP_DOWN) {
                y = ground;
                vy = 0;
                fell = 0;
            } else {
                grounded = false;
                vy = Math.max(TERMINAL, vy - GRAVITY * SUB);
                double ny = y + vy * SUB / LU;
                fell += Math.max(0.0, y - ny);
                if (!Double.isNaN(ground) && ny <= ground) {
                    ny = ground;
                    grounded = true;
                    vy = 0;
                    fell = 0;
                }
                y = ny;
            }
            // a wall (COLL_STEP_TRIGGER_WEAPON_REACT): something standing more than the kart's own step-up (1.25 blocks) above where the bomb rides. The ride height is the field's
            // smoothed surface, which is up to half a block (RIDE_LAG) under the real top of a steep slope's crest: without allowing for that, the block that follows a crest is a
            // wall (and out of the window), which is where a bomb that had climbed a slope blew up; the bomb stays where it was
            double[] wall = field.blockingBox(nx, nz, y + TerrainField.RIDE_LAG + TerrainField.STEP_UP, y + TerrainField.RIDE_LAG + TerrainField.STEP_UP + 0.4, WALL_HALF);
            if (wall != null) {
                // look again at a window rebuilt where the bomb is, so a stale one cannot stop it, and only then explode
                field.prepare(getX(), getY() + TerrainField.RIDE_LAG, getZ(), 0.0);
                prepX = getX();
                prepY = getY();
                prepZ = getZ();
                double[] again = field.blockingBox(nx, nz, y + TerrainField.RIDE_LAG + TerrainField.STEP_UP, y + TerrainField.RIDE_LAG + TerrainField.STEP_UP + 0.4, WALL_HALF);
                if (again != null) {
                    explode(level, String.format("a wall: box (%.2f %.2f %.2f)-(%.2f %.2f %.2f) at ride height %.2f, ground %.2f, bomb at %.2f %.2f %.2f going to %.2f %.2f",
                            again[0], again[1], again[2], again[3], again[4], again[5], y, ground, getX(), getY(), getZ(), nx, nz));
                    return;
                }
            }
            setPos(nx, y, nz);
            setYRot((float) Math.toDegrees(heading));
            breakCrates(level);
            if (fell > MAX_FALL || getY() < level.getMinBuildHeight() - 8) {
                explode(level, String.format("no ground below: fell %.1f blocks, at %.2f %.2f %.2f", fell, getX(), getY(), getZ()));              // 0x900 lu
                return;
            }
            if (hitSomething(level)) {
                explode(level, "it hit " + hitWhat);
                return;
            }
        }
    }

    /** RB_Hazard_CollideWithDrivers (a kart other than the owner's, or the owner's once the safety frames have passed) and RB_Hazard_CollideWithBucket (mines). */
    private String hitWhat = "";

    private boolean hitSomething(ServerLevel level) {
        AABB reach = getBoundingBox().inflate(HIT_RADIUS + 0.5, HIT_HEIGHT, HIT_RADIUS + 0.5);
        for (KartEntity kart : level.getEntitiesOfClass(KartEntity.class, reach)) {
            if (!(kart.getControllingPassenger() instanceof ServerPlayer driver)) continue;
            if (driver.getUUID().equals(owner) && tickCount < OWNER_SPARED_TICKS) continue;
            double dx = kart.getX() - getX();
            double dz = kart.getZ() - getZ();
            if (dx * dx + dz * dz < HIT_RADIUS * HIT_RADIUS && Math.abs(kart.getY() - getY()) < HIT_HEIGHT) {
                hitWhat = "a kart (" + driver.getGameProfile().getName() + (driver.getUUID().equals(owner) ? ", its own driver" : "") + ")";
                return true;
            }
        }
        for (Entity e : level.getEntities(this, reach)) {
            if (e instanceof MinePool.Mine mine && !mine.isGone() && e.distanceToSqr(this) < HIT_RADIUS * HIT_RADIUS) {
                hitWhat = "a mine (" + e.getType().getDescriptionId() + " at " + e.blockPosition().toShortString() + ")";
                return true;
            }
        }
        return false;
    }

    /**
     * RB_Hazard_CollLevInst: a crate the bomb rolls into breaks and the bomb goes on (the call returns 0 for PU_FRUIT_CRATE / PU_RANDOM_CRATE); its driver gets what a kart
     * would (RB_CrateFruit_ThCollide / RB_CrateWeapon_ThCollide with a bomb as the hitter).
     */
    private void breakCrates(ServerLevel level) {
        ServerPlayer driver = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (driver == null) return;
        for (PickupEntity crate : level.getEntitiesOfClass(PickupEntity.class, getBoundingBox().inflate(0.4))) {
            PickupKind kind = crate.kind();
            if (kind == PickupKind.FRUIT_CRATE || kind == PickupKind.POWERUP_CRATE) crate.breakBy(driver);
        }
    }

    /** What the burst does to everything that is not a kart: the mod's entity interaction (docs: on by default, tuned by the config's entities section). */
    private void hurtOthers(ServerLevel level, AABB blast, double radius) {
        ServerPlayer from = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        for (Entity e : level.getEntities(this, blast)) {
            if (e instanceof KartEntity || e instanceof MinePool.Mine || e instanceof PickupEntity || e instanceof BombEntity || e.getVehicle() instanceof KartEntity) continue;
            double dist = e.position().add(0.0, e.getBbHeight() / 2.0, 0.0).distanceTo(position());
            if (dist >= radius) continue;
            double impact = 1.0 - dist / radius;
            if (CtrConfig.ENTITY_DAMAGE.get() && e instanceof LivingEntity living) {
                boolean harmable = !(e instanceof Player other) || (CtrConfig.ENTITY_DAMAGE_PLAYERS.get() && from != null && from.server.isPvpAllowed() && from.canHarmPlayer(other));
                if (harmable) living.hurt(level.damageSources().explosion(this, from), (float) (((int) ((impact * impact + impact) / 2.0 * 7.0 * radius + 1.0)) * CtrConfig.ENTITY_DAMAGE_SCALE.get()));
            }
            if (CtrConfig.ENTITY_CONTACT.get()) {
                Vec3 away = e.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * impact * 1.2, 0.3 + impact * 0.6, away.z * impact * 1.2));
                e.hurtMarked = true;
            }
        }
    }

    /** RB_MovingExplosive_Explode + RB_Burst_Init. */
    private void explode(ServerLevel level, String cause) {
        com.ctrcraft.CtrCraftMod.LOGGER.info("Bomb exploded: {}", cause);
        double radius = powered() ? BLAST_RADIUS_POWERED : BLAST_RADIUS;
        AABB blast = new AABB(position(), position()).inflate(radius);
        List<KartEntity> karts = level.getEntitiesOfClass(KartEntity.class, blast);
        for (KartEntity kart : karts) {
            if (!(kart.getControllingPassenger() instanceof ServerPlayer driver) || driver.getUUID().equals(owner)) continue;   // "the firing driver is excluded"
            if (kart.distanceToSqr(this) >= radius * radius) continue;
            ItemManager.applyEffect(driver, ItemManager.EFFECT_BLAST);
            ItemManager.loseWumpa(driver, ItemManager.BLAST_WUMPA_PENALTY);
        }
        // the burst sets off the mines it reaches (PROC_CollideHitboxWithBucket on the MINE bucket)
        for (Entity e : level.getEntities(this, blast)) {
            if (e instanceof MinePool.Mine mine && !mine.isGone() && e.distanceToSqr(this) < radius * radius) mine.overflow(level);
        }
        hurtOthers(level, blast, radius);
        // the burst: a starburst sized to the blast (its size rides in the particle's x velocity), and the explosion's sound (PlaySound3D 0x49)
        level.sendParticles(ModParticles.BURST.get(), getX(), getY() + 0.4, getZ(), 0, radius, 0.0, 0.0, 1.0);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BOMB_EXPLODE.get(), SoundSource.PLAYERS, 4.0F, 1.0F);
        discard();
    }

    private void vanish() {
        ((ServerLevel) level()).sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (counted) {
            counted = false;
            ACTIVE.decrementAndGet();
        }
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;                  // the fall is tick()'s
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
