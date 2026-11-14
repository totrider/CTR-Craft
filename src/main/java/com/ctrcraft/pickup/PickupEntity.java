package com.ctrcraft.pickup;

import com.ctrcraft.kart.KartEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A fruit or crate in the world: spawned and removed by its {@link PickupSpotBlockEntity}, never saved. It spins (the renderer's job) and, on the
 * server, is taken by the first driven kart that touches it. What touching does is {@link ItemManager}'s.
 */
public class PickupEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(PickupEntity.class, EntityDataSerializers.INT);

    private BlockPos spot;

    public PickupEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public PickupKind kind() {
        return PickupKind.byOrdinal(entityData.get(DATA_KIND));
    }

    public void setKind(PickupKind kind) {
        entityData.set(DATA_KIND, kind.ordinal());
    }

    /** The entity event that tells the clients a crate broke (the pieces fly): sent just before the entity is removed. */
    public static final byte EVENT_BREAK = 77;

    /** A crate (not a fruit) breaks: its pieces fly on every client that sees it. */
    private void breakUp(ServerLevel level) {
        if (kind() != PickupKind.FRUIT) level.broadcastEntityEvent(this, EVENT_BREAK);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id != EVENT_BREAK) {
            super.handleEntityEvent(id);
            return;
        }
        // the weapon crate's break is turned at random about the vertical axis (RB_CrateWeapon_ThCollide), the fruit crate's is not
        double turn = kind() == PickupKind.POWERUP_CRATE ? random.nextInt(360) : 0.0;
        level().addParticle(com.ctrcraft.ModParticles.CRATE_BREAK.get(), getX(), getY(), getZ(), turn, 0.0, 0.0);
    }

    public void setSpot(BlockPos spot) {
        this.spot = spot;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel level)) return;
        if (spot == null || !(level.getBlockEntity(spot) instanceof PickupSpotBlockEntity be) || !be.owns(this)) {
            discard();
            return;
        }
        // the mechanics' size in lu: the pickup's own width (a crate is 108.8 lu, the picture is one block) plus the kart's collision sphere, over the mechanics' scale
        double half = (kind().width * com.ctrcraft.kart.KartScale.CRATE_LU / 2.0 + com.ctrcraft.physics.KartSim.SPHERE_RADIUS) / com.ctrcraft.kart.KartScale.LU_PER_BLOCK;
        AABB reach = new AABB(getX() - half, getY() - 0.4, getZ() - half, getX() + half, getY() + kind().height + 0.2, getZ() + half);
        for (KartEntity kart : level.getEntitiesOfClass(KartEntity.class, reach)) {
            if (kart.getControllingPassenger() instanceof ServerPlayer driver && ItemManager.collect(this, be, driver)) {
                be.taken(this);
                breakUp(level);
                discard();
                return;
            }
        }
    }

    /**
     * A bomb rolled into this crate (RB_CrateWeapon_ThCollide / RB_CrateFruit_ThCollide with a DYNAMIC_BOMB hitter): the crate breaks and its driver gets what a kart
     * touching it would, the wumpa or the roulette. Returns whether it was used up.
     */
    public boolean breakBy(ServerPlayer driver) {
        if (!(level() instanceof ServerLevel level) || spot == null || !(level.getBlockEntity(spot) instanceof PickupSpotBlockEntity be) || !be.owns(this)) return false;
        if (ItemManager.collect(this, be, driver)) {
            be.taken(this);
            breakUp(level);
            discard();
            return true;
        }
        return false;
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
