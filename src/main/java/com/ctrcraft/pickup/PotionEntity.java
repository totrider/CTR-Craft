package com.ctrcraft.pickup;

import java.util.UUID;

import com.ctrcraft.ModSounds;
import com.ctrcraft.kart.KartEntity;

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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * N. Brio's Beaker (docs/POWERUPS.md): a green one, a red one (a kart with ten wumpa). It is dropped behind the kart, where it stays, or thrown forward in an arc
 * (RB_Potion_ThTick_InAir): it bursts on a wall, and lands as a beaker on the ground. Any other kart than the dropper's (for the first 10 frames) whose centre comes within
 * 80 lu (1.6 blocks) of it spins out (RB_GenericMine_ThTick, damage type SPIN) and the beaker bursts with the glass sound; the red one also puts a rain cloud over the kart
 * ({@link ItemManager#cloud}). Speeds are the game's, in lu per frame of 30 Hz, with 50 lu to the block.
 */
public class PotionEntity extends Entity implements MinePool.Mine {
    private static final EntityDataAccessor<Integer> DATA_TILT = SynchedEntityData.defineId(PotionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RED = SynchedEntityData.defineId(PotionEntity.class, EntityDataSerializers.BOOLEAN);

    /** POTION_THROW_SPEED 0x78 and POTION_THROW_Y_VELOCITY 0x30, lu per frame. */
    private static final double THROW_SPEED = 120.0;
    private static final double THROW_UP = 48.0;
    /** Gravity: (elapsedTimeMS << 2) >> 5 per frame, with a terminal velocity of -0x60. */
    private static final double GRAVITY = 4.0;
    private static final double TERMINAL = -96.0;
    /** A game tick is 1.5 frames of the original. */
    private static final double FRAMES_PER_TICK = 1.5;
    private static final double LU = com.ctrcraft.kart.KartScale.LU_PER_BLOCK;
    private static final double HIT_RADIUS = 80.0 / LU;      // sqrt(0x1900) = 80 lu
    private static final int OWNER_SPARED_TICKS = 7;        // MINE_PARENT_SAFETY_FRAMES

    private String pool;
    private UUID owner;
    private boolean thrown;
    private double vx;
    private double vy;
    private double vz;
    private int airTicks;
    private boolean tilted;
    private int restSignature;

    public PotionEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public boolean red() {
        return entityData.get(DATA_RED);
    }

    /** The slope it lies on (see {@link GroundTilt}); flat while it flies. */
    public int tilt() {
        return entityData.get(DATA_TILT);
    }

    public void setRed(boolean red) {
        entityData.set(DATA_RED, red);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    /** POTION_THROW_FORWARD: off in an arc along (dx, dz), a unit vector. */
    public void throwFrom(double dx, double dz) {
        thrown = true;
        vx = dx * THROW_SPEED * com.ctrcraft.CtrConfig.WEAPON_SPEED_SCALE.get();
        vz = dz * THROW_SPEED * com.ctrcraft.CtrConfig.WEAPON_SPEED_SCALE.get();
        vy = THROW_UP;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RED, false);
        builder.define(DATA_TILT, GroundTilt.FLAT);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (pool == null) {
            pool = MinePool.poolOf(owner != null ? owner : getUUID());
            MinePool.add(pool, this, level);
        }
        if (!MinePool.inRace(pool) && tickCount > MinePool.FREE_ROAM_LIFETIME_TICKS) {
            discard();
            return;
        }
        if (thrown) {
            airTicks++;
            Vec3 mv = new Vec3(vx * FRAMES_PER_TICK / LU, vy * FRAMES_PER_TICK / LU, vz * FRAMES_PER_TICK / LU);
            vy = Math.max(TERMINAL, vy - GRAVITY * FRAMES_PER_TICK);
            move(MoverType.SELF, mv);
            if (horizontalCollision || airTicks > 200) {
                shatter(level);                    // a wall: it bursts
            } else if (verticalCollision && mv.y < 0) {
                thrown = false;                    // it lands and stays where it is
            }
            return;
        }
        if (!tilted) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0).multiply(0.6, 0.98, 0.6));
            move(MoverType.SELF, getDeltaMovement());
            if (onGround()) {
                tilted = true;
                GroundTilt.Rest r = GroundTilt.rest(level, this, getX(), getY(), getZ());
                entityData.set(DATA_TILT, r.tilt());
                setPos(getX(), r.y(), getZ());
                restSignature = GroundTilt.signature(level, getX(), getY(), getZ());
            }
        } else if (tickCount % 20 == 0 && GroundTilt.signature(level, getX(), getY(), getZ()) != restSignature) {
            // the blocks under it changed: wake, fall if there is nothing to lie on, and settle again
            tilted = false;
            entityData.set(DATA_TILT, GroundTilt.FLAT);
        }
        AABB reach = getBoundingBox().inflate(HIT_RADIUS + 0.2);
        for (KartEntity kart : level.getEntitiesOfClass(KartEntity.class, reach)) {
            if (!(kart.getControllingPassenger() instanceof ServerPlayer driver)) continue;
            if (driver.getUUID().equals(owner) && tickCount < OWNER_SPARED_TICKS) continue;
            if (kart.distanceToSqr(getX(), getY() + 0.3, getZ()) >= HIT_RADIUS * HIT_RADIUS) continue;
            ItemManager.applyEffect(driver, ItemManager.EFFECT_SPIN);
            ItemManager.loseWumpa(driver, 1);                 // VehPhysProc_SpinFirst_Init: a spin costs a wumpa
            if (red()) ItemManager.cloud(driver, !ItemManager.hasCloud(driver));
            shatter(level);
            return;
        }
    }

    @Override
    public void overflow(ServerLevel level) {
        shatter(level);
    }

    @Override
    public void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        discard();
    }

    @Override
    public boolean isGone() {
        return isRemoved();
    }

    private void shatter(ServerLevel level) {
        level.playSound(null, blockPosition(), ModSounds.GLASS_SHATTER.get(), SoundSource.PLAYERS, 4.0F, 1.0F);
        level.sendParticles(red() ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.3, getZ(), 24, 0.4, 0.3, 0.4, 0.05);
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
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
