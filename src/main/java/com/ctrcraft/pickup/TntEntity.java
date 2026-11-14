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
 * The TNT power-up's crate, dropped behind the kart (docs/POWERUPS.md): the first kart to run into it gets it on its head and is slowed, and it explodes three seconds
 * later, spinning that kart out. A Nitro crate (dropped by a driver with ten wumpa) explodes at the slightest contact. The one who dropped it is spared for a moment, so it
 * is not hit by its own drop. The explosion hurts and throws other entities too (like the rest of the mod's entity interaction), and breaks no blocks.
 */
public class TntEntity extends Entity implements MinePool.Mine {
    private static final EntityDataAccessor<Boolean> DATA_NITRO = SynchedEntityData.defineId(TntEntity.class, EntityDataSerializers.BOOLEAN);
    /** Game ticks left on the fuse once it is on a kart, or -1 while it lies on the ground. */
    /** The id of the kart the crate sits on (-1 when none): the renderer puts it on that kart's head every frame, not where the 20 Hz updates last left it. */
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(TntEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TILT = SynchedEntityData.defineId(TntEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData.defineId(TntEntity.class, EntityDataSerializers.INT);

    /** CTR: the crate explodes three seconds after it lands on a kart. */
    public static final int FUSE_TICKS = 60;
    /** The dropper cannot trigger it for this long, game ticks. */
    private static final int OWNER_SPARED_TICKS = 7;          // MINE_PARENT_SAFETY_FRAMES: 10 frames at 30 Hz
    /** RB_GenericMine_ThTick: a kart whose centre is within sqrt(0x3840) = 120 lu (2.4 blocks) of the crate, in 3D, is hit. */
    private static final double HIT_RADIUS = 120.0 / com.ctrcraft.kart.KartScale.LU_PER_BLOCK;      // 120 lu, as it was at 50 lu a block (2.4)
    /** A crate nobody hits goes away after this long (a minute), game ticks. */
    private static final int LIFETIME_TICKS = 1200;

    /** The frames the honk plays on, of the 90 on the head, as game ticks of the 60: 0, 20, 40, 60, 70, 80 frames at 30 Hz. */
    private static final int[] HONK_AT = { 0, 13, 27, 40, 47, 53 };
    /** The flight onto the head before the fuse starts (ThrowOnHead): the landing sound plays at its end. */
    private static final int LAND_TICKS = 6;
    /** A crate thrown off a head goes up and comes down; it blows up (harmlessly) when it is back at the height of the head. */
    private double fallFromY;
    private boolean thrownOff;
    private int airTicks;
    private boolean tilted;
    private int restSignature;

    private String pool;
    private UUID owner;
    private UUID victim;
    /** Whoever the crate was shaken off or dropped by cannot set it off again until this tick of its life. */
    private UUID spared;
    private int sparedUntil;

    public TntEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public boolean nitro() {
        return entityData.get(DATA_NITRO);
    }

    public void setNitro(boolean nitro) {
        entityData.set(DATA_NITRO, nitro);
    }

    public int fuse() {
        return entityData.get(DATA_FUSE);
    }

    /** The slope it lies on (see {@link GroundTilt}); flat while it falls. */
    public int tilt() {
        return entityData.get(DATA_TILT);
    }

    public int targetKart() {
        return entityData.get(DATA_TARGET);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_NITRO, false);
        builder.define(DATA_FUSE, -1);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_TILT, GroundTilt.FLAT);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        int fuse = fuse();
        if (fuse >= 0) {
            ServerPlayer driver = victim == null ? null : level.getServer().getPlayerList().getPlayer(victim);
            if (driver != null && driver.getVehicle() instanceof KartEntity kart) {
                setPos(kart.getX(), kart.getY() + 0.9, kart.getZ());     // on the kart's head
            }
            // RB_TNT_ThTick_SitOnHead: a driver already spun, crashed or blasted sets it off at once
            if (--fuse <= 0) {
                explode(level, driver);
                return;
            }
            if (fuse == FUSE_TICKS) {
                level.playSound(null, blockPosition(), ModSounds.TNT_LAND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                jumpsRemaining = 8 - new int[] { -1, 0, 0, 1 }[random.nextInt(4)];        // the original's 8 - (rng % 2) with a signed rng: 9, 8 or 7
            }
            if (fuse > FUSE_TICKS) {
                entityData.set(DATA_FUSE, fuse);
                return;
            }
            int elapsed = FUSE_TICKS - fuse;
            for (int h : HONK_AT) {
                if (h == elapsed) level.playSound(null, blockPosition(), ModSounds.TNT_TICK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            entityData.set(DATA_FUSE, fuse);
            return;
        }
        if (thrownOff) {
            // RB_TNT_ThTick_ThrowOffHead: up, then down; the explosion on the way back to the head's height is the picture and the sound only
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            move(MoverType.SELF, getDeltaMovement());
            airTicks++;
            // up and down again: it explodes when it comes down on something (or, if it never does, after five seconds)
            if ((airTicks > 6 && (onGround() || getY() < fallFromY - 8.0)) || airTicks > 100) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.4, getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, blockPosition(), (nitro() ? ModSounds.GLASS_SHATTER : ModSounds.TNT_EXPLODE).get(), SoundSource.PLAYERS, 4.0F, 1.0F);
                discard();
            }
            return;
        }
        if (pool == null) {
            pool = MinePool.poolOf(owner != null ? owner : getUUID());
            MinePool.add(pool, this, level);
        }
        // lying on the track: fall to the ground
        if (!tilted) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0).multiply(0.6, 0.98, 0.6));
            move(MoverType.SELF, getDeltaMovement());
            if (onGround()) {
                // it lies on the surface it came to rest on, flat on its plane (the blocks' highest point under it would hold it above a slope), and stays there
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
        if (!MinePool.inRace(pool == null ? "free:" : pool) && tickCount > MinePool.FREE_ROAM_LIFETIME_TICKS) {
            discard();
            return;
        }
        AABB reach = getBoundingBox().inflate(HIT_RADIUS + 0.2);
        for (KartEntity kart : level.getEntitiesOfClass(KartEntity.class, reach)) {
            if (kart.getControllingPassenger() == null || kart.distanceToSqr(getX(), getY() + 0.4, getZ()) >= HIT_RADIUS * HIT_RADIUS) continue;
            if (!(kart.getControllingPassenger() instanceof ServerPlayer driver)) continue;
            if (driver.getUUID().equals(owner) && tickCount < OWNER_SPARED_TICKS) continue;
            if (driver.getUUID().equals(spared) && tickCount < sparedUntil) continue;
            if (nitro()) {
                victim = driver.getUUID();
                explode(level, driver);
            } else {
                // RB_GenericMine_ThTick: the crate is thrown onto the head (the "bounce" sound), lands there a moment later and the fuse starts
                victim = driver.getUUID();
                MinePool.remove(pool, this);                 // RB_MinePool_Remove: a crate that has landed on a head is no longer a mine on the track
                entityData.set(DATA_TARGET, kart.getId());
                entityData.set(DATA_FUSE, FUSE_TICKS + LAND_TICKS);
                ItemManager.applyEffect(driver, ItemManager.EFFECT_SLOW);
                level.playSound(null, blockPosition(), ModSounds.TNT_HIT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return;
        }
    }

    /** The jumps still allowed before the next one throws the crate off: set when it lands on the head, 8 minus a random -1, 0 or 1 (RB_TNT_ThTick_ThrowOnHead). */
    private int jumpsRemaining;

    /**
     * RB_TNT_ThTick_SitOnHead: a jump that starts while the crate sits on the head (not while it is still flying onto it) uses up one of {@link #jumpsRemaining};
     * when none is left the next jump throws it off.
     */
    public void hop(ServerPlayer driver) {
        if (fuse() < 0 || fuse() > FUSE_TICKS || victim == null || !victim.equals(driver.getUUID())) return;
        if (jumpsRemaining != 0) {
            jumpsRemaining--;
            return;
        }
        shakeOff(driver);
    }

    /** RB_TNT_Detach: the driver jumped it off. It lands behind the kart as a crate on the track again, and cannot catch that driver for a moment. */
    public void shakeOff(ServerPlayer driver) {
        if (fuse() < 0 || victim == null || !victim.equals(driver.getUUID()) || !(driver.getVehicle() instanceof KartEntity kart)) return;
        fallFromY = kart.getY() + 0.2;
        airTicks = 0;
        entityData.set(DATA_TARGET, -1);
        setPos(kart.getX(), kart.getY() + 0.9, kart.getZ());
        setDeltaMovement(0.0, 0.5, 0.0);
        thrownOff = true;
        entityData.set(DATA_FUSE, -1);
        victim = null;
        ItemManager.applyEffect(driver, ItemManager.EFFECT_CLEAR);
    }

    @Override
    public void overflow(ServerLevel level) {
        if (fuse() >= 0) return;
        level.playSound(null, blockPosition(), (nitro() ? ModSounds.GLASS_SHATTER : ModSounds.TNT_EXPLODE).get(), SoundSource.PLAYERS, 4.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.4, getZ(), 1, 0, 0, 0, 0);
        discard();
    }

    @Override
    public void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.4, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        discard();
    }

    @Override
    public boolean isGone() {
        return isRemoved();
    }

    private void explode(ServerLevel level, ServerPlayer driver) {
        if (driver != null && driver.getVehicle() instanceof KartEntity) {
            ItemManager.applyEffect(driver, ItemManager.EFFECT_BLAST);
            ItemManager.loseWumpa(driver, ItemManager.BLAST_WUMPA_PENALTY);
        }
        level.explode(this, null, null, getX(), getY() + 0.5, getZ(), nitro() ? 3.0F : 2.0F, false, Level.ExplosionInteraction.NONE, ParticleTypes.EXPLOSION,
                ParticleTypes.EXPLOSION_EMITTER, nitro() ? ModSounds.GLASS_SHATTER : ModSounds.TNT_EXPLODE);
        discard();
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
