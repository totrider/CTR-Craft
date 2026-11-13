package com.ctrcraft.pickup;

import java.util.UUID;

import com.ctrcraft.CtrConfig;
import com.ctrcraft.ModBlockEntities;
import com.ctrcraft.ModEntities;
import com.ctrcraft.track.RaceManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a pickup spot does. While it is <b>active</b> it keeps its pickup in the world; when not, it keeps none. A spot is active when a race on
 * a track near it is on (the track's nodes within the configured radius, see {@link RaceManager#activeNear}) or when it has a redstone signal,
 * which lets it work without any track (for testing, and for builds that are not races). A power-up crate spot can be set to always hold one
 * particular power-up instead of the place-based roulette.
 */
public class PickupSpotBlockEntity extends BlockEntity {
    /** The power-up this spot always gives ("" for CTR's roulette). Only used by power-up crate spots. */
    private String itemId = "";
    private UUID pickup;
    private boolean active;
    private int respawn;
    /** A fruit that was taken stays gone until the spot has been inactive. */
    private boolean spent;
    /** A redstone signal switched the spot on: it keeps its pickup (and respawns a crate) after the signal is gone, until a race takes over or the block is removed. */
    private boolean armed;
    private boolean signal;
    private boolean raceActive;

    public PickupSpotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PICKUP_SPOT.get(), pos, state);
    }

    public PickupKind kind() {
        return ((PickupSpotBlock) getBlockState().getBlock()).kind();
    }

    public String itemId() {
        return itemId;
    }

    public void setItemId(String id) {
        this.itemId = id;
        setChanged();
    }

    public boolean owns(Entity e) {
        return e.getUUID().equals(pickup);
    }

    /** The pickup was taken by a kart. */
    public void taken(PickupEntity e) {
        pickup = null;
        if (kind().respawnTicks > 0) respawn = kind().respawnTicks;
        else spent = true;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PickupSpotBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        // RB_CrateAny_ThTick_Grow: the respawn waits while a kart or a mine (a TNT crate...) sits in the way
        boolean blocked = blocked(server, pos);
        if (be.respawn > 0 && !blocked) be.respawn--;
        // a redstone signal arriving arms the spot and brings back what was taken (a pulse again brings back a fruit that was taken)
        boolean signal = server.hasNeighborSignal(pos);
        if (signal && !be.signal) {
            be.armed = true;
            be.spent = false;
            be.respawn = 0;
            be.setChanged();
        }
        be.signal = signal;
        if (server.getGameTime() % 10 == 0) {
            boolean race = RaceManager.activeNear(server, pos, CtrConfig.PICKUP_TRACK_RADIUS.get());
            if (race && !be.raceActive) {
                be.spent = false;                    // a race begins: everything is there again
                be.respawn = 0;
            }
            be.raceActive = race;
            boolean now = race || be.armed;
            if (now != be.active) {
                be.active = now;
                if (!now) {
                    be.spent = false;
                    be.respawn = 0;
                }
            }
        }
        Entity current = be.pickup == null ? null : server.getEntity(be.pickup);
        if (current != null && current.isRemoved()) current = null;
        if (!be.active) {
            if (current != null) current.discard();
            be.pickup = null;
            return;
        }
        if (current == null && be.respawn == 0 && !be.spent && !blocked) {
            PickupEntity e = ModEntities.PICKUP.get().create(server);
            if (e == null) return;
            e.setKind(be.kind());
            e.setSpot(pos);
            e.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
            server.addFreshEntity(e);
            be.pickup = e.getUUID();
        }
    }

    /** A kart or a placed power-up (TNT, Nitro) is on the spot. */
    private static boolean blocked(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos).inflate(0.5, 0.3, 0.5);
        return !level.getEntitiesOfClass(com.ctrcraft.kart.KartEntity.class, box).isEmpty() || !level.getEntitiesOfClass(TntEntity.class, box).isEmpty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Item", itemId);
        tag.putBoolean("Armed", armed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        itemId = tag.getString("Item");
        armed = tag.getBoolean("Armed");
    }
}
