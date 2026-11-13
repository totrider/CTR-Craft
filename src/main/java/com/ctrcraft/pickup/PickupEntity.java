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
        // the pickup's own size (the entity's box is the largest of them) plus the kart's half width: a kart touching its side takes it
        double half = kind().width / 2.0 + 0.55;
        AABB reach = new AABB(getX() - half, getY() - 0.4, getZ() - half, getX() + half, getY() + kind().height + 0.2, getZ() + half);
        for (KartEntity kart : level.getEntitiesOfClass(KartEntity.class, reach)) {
            if (kart.getControllingPassenger() instanceof ServerPlayer driver && ItemManager.collect(this, be, driver)) {
                be.taken(this);
                discard();
                return;
            }
        }
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
