package com.ctrcraft.kart;

import com.ctrcraft.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * A drivable kart. The driver's client runs the CTR physics (see {@code client/KartDriver}) and moves this entity;
 * the vanilla "move vehicle" packet carries the position to the server and from there to everybody else. The server
 * does not simulate a driven kart (it only validates the move, as it does for boats). An empty kart just falls and stops.
 */
public class KartEntity extends Entity {
    /** CTR's drawn yaw (heading + drift/spin offset), 4096ths of a turn, so other players see the drift angle. */
    private static final EntityDataAccessor<Integer> DATA_DRAWN_YAW =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);

    /** Engine sound: distortion (low byte) | volume << 8, CTR's own scale (see KartSim.engineSound). */
    private static final EntityDataAccessor<Integer> DATA_ENGINE =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    /** The ground normal the kart leans on, x and z as bytes (value = n * 127 + 127); 0x7F7F is flat. */
    /** The kart's colour (RGB): what the tintable parts of its model (the tub, the struts, the seat) are multiplied by. A dye changes it. */
    private static final EntityDataAccessor<Integer> DATA_COLOUR =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    public static final int DEFAULT_COLOUR = 0xD63434;
    /** The seat's colour (tint index 1 of the model). A dye used while sneaking changes it. */
    private static final EntityDataAccessor<Integer> DATA_SEAT_COLOUR =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    public static final int DEFAULT_SEAT_COLOUR = 0xF0C43C;
    private static final EntityDataAccessor<Integer> DATA_TILT =
            SynchedEntityData.defineId(KartEntity.class, EntityDataSerializers.INT);
    public static final int TILT_FLAT = 0x7F7F;

    // client-side smoothing of a kart somebody else drives
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot;
    private int lerpSteps;
    private float yawPrevDeg, yawDeg;

    public KartEntity(EntityType<? extends KartEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DRAWN_YAW, 0);
        builder.define(DATA_FLAGS, 0);
        builder.define(DATA_ENGINE, 0x823C);
        builder.define(DATA_TILT, TILT_FLAT);
        builder.define(DATA_COLOUR, DEFAULT_COLOUR);
        builder.define(DATA_SEAT_COLOUR, DEFAULT_SEAT_COLOUR);
    }

    public int colour() {
        return entityData.get(DATA_COLOUR);
    }

    public int seatColour() {
        return entityData.get(DATA_SEAT_COLOUR);
    }

    public void setSeatColour(int rgb) {
        entityData.set(DATA_SEAT_COLOUR, rgb & 0xFFFFFF);
    }

    public void setColour(int rgb) {
        entityData.set(DATA_COLOUR, rgb & 0xFFFFFF);
    }

    public int getTilt() {
        return entityData.get(DATA_TILT);
    }

    public void setTilt(int tilt) {
        entityData.set(DATA_TILT, tilt);
    }

    /** The driver's steering as the animation shows it, -1 left to +1 right (bits 16-20 of the engine word: 0 unset, 1 to 29, 15 straight). */
    public float steer() {
        int code = (getEngineData() >> 16) & 31;
        return code == 0 ? 0.0F : (code - 15) / 14.0F;
    }

    public int getEngineData() {
        return entityData.get(DATA_ENGINE);
    }

    public void setEngineData(int engine) {
        entityData.set(DATA_ENGINE, engine);
    }

    public int getDrawnYawAngle() {
        return entityData.get(DATA_DRAWN_YAW);
    }

    public void setDrawnYawAngle(int angle) {
        entityData.set(DATA_DRAWN_YAW, angle);
    }

    public int getVisualFlags() {
        return entityData.get(DATA_FLAGS);
    }

    public void setVisualFlags(int flags) {
        entityData.set(DATA_FLAGS, flags);
    }

    /** The yaw to draw the model with, degrees (Minecraft convention). The local driver's is exact; others' is smoothed. */
    public float renderYawDegrees(float partialTick) {
        if (isLocallyDriven()) {
            return KartScale.angleToYaw(getDrawnYawAngle());
        }
        return Mth.rotLerp(partialTick, yawPrevDeg, yawDeg);
    }

    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private float wheelSpin;
    private float wheelSpinO;

    /** How far the wheels have turned, degrees, positive rolling forward (client side: worked out from how far the kart has moved along the way it faces). */
    public float wheelSpin(float partialTick) {
        return Mth.lerp(partialTick, wheelSpinO, wheelSpin);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            wheelSpinO = wheelSpin;
            if (!Double.isNaN(lastX)) {
                double yaw = Math.toRadians(yawDeg);
                double along = (getX() - lastX) * -Math.sin(yaw) + (getZ() - lastZ) * Math.cos(yaw);
                wheelSpin += (float) Math.toDegrees(along / KartWheels.radius());
            }
            lastX = getX();
            lastZ = getZ();
            yawPrevDeg = yawDeg;
            yawDeg = KartScale.angleToYaw(getDrawnYawAngle());
            if (FMLEnvironment.dist.isClient()) {
                com.ctrcraft.client.KartEffects.tick(this);
            }
            if (!isLocallyDriven() && lerpSteps > 0) {
                double f = 1.0 / lerpSteps;
                setPos(Mth.lerp(f, getX(), lerpX), Mth.lerp(f, getY(), lerpY), Mth.lerp(f, getZ(), lerpZ));
                setYRot(Mth.rotLerp((float) f, getYRot(), lerpYRot));
                lerpSteps--;
            }
        } else if (getControllingPassenger() == null) {
            // nobody drives: the effects the driver left behind (wheelie, flame, skid) end with them
            if (getVisualFlags() != 0) {
                setVisualFlags(0);
            }
            if (getEngineData() != 0x823C) {
                setEngineData(0x823C);
            }
            if (restTilted) {
                // asleep on a slope: nothing to compute until the blocks under it change (looked at about once a second)
                if (tickCount % 20 == 0 && com.ctrcraft.pickup.GroundTilt.signature(level(), getX(), getY(), getZ()) != restSignature) restTilted = false;
                return;
            }
            // fall, stop
            Vec3 dm = getDeltaMovement().add(0, -0.04, 0);
            setDeltaMovement(dm.x * 0.6, dm.y * 0.98, dm.z * 0.6);
            move(MoverType.SELF, getDeltaMovement());
            // an empty kart that has come to rest lies on the slope it is on (the driver's tilt would otherwise snap back to flat), flat on the slope's plane, once
            if (onGround() && getDeltaMovement().lengthSqr() < 1.0E-4) {
                restTilted = true;
                com.ctrcraft.pickup.GroundTilt.Rest r = com.ctrcraft.pickup.GroundTilt.rest(level(), this, getX(), getY(), getZ());
                setTilt(r.tilt());
                setPos(getX(), r.y(), getZ());
                restSignature = com.ctrcraft.pickup.GroundTilt.signature(level(), getX(), getY(), getZ());
            } else if (getTilt() != TILT_FLAT) {
                setTilt(TILT_FLAT);
            }
        } else {
            restTilted = false;
        }
    }

    private boolean restTilted;
    private int restSignature;
    private int rejectedMoves;

    /** Server: counts a driver report that was refused; returns how many in a row. */
    public int noteRejectedMove() {
        return ++rejectedMoves;
    }

    public void clearRejectedMoves() {
        rejectedMoves = 0;
    }

    /** Client: the local player is the one driving this kart (their client runs its physics and owns its position). */
    public boolean isLocallyDriven() {
        return level().isClientSide && getFirstPassenger() instanceof Player player && player.isLocalPlayer();
    }

    /**
     * Never "controlled by the local instance" in vanilla's sense: that would make the client send vanilla's move-vehicle packet,
     * which the server re-validates with box collisions and rolls back. Position travels in {@code KartStatePayload} instead.
     */
    @Override
    public boolean isControlledByLocalInstance() {
        return false;
    }

    /** Forget any pending smoothing target (a stale one would snap a just-left kart back). */
    public void clearLerp() {
        lerpSteps = 0;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (isLocallyDriven()) {
            return; // the driver's own client is the authority on where its kart is
        }
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = Math.max(steps, 1);
    }

    /** What the kart carries (wumpa fruit and the held power-up, {@link com.ctrcraft.pickup.ItemManager}): it stays with the kart when the driver gets out. */
    private final com.ctrcraft.pickup.ItemManager.State items = new com.ctrcraft.pickup.ItemManager.State();

    public com.ctrcraft.pickup.ItemManager.State items() {
        return items;
    }

    /** Whether driving this kart into a Start node's area can start a race (the Wrench switches it). Kept on the server. */
    private boolean canStartRaces = true;

    public boolean canStartRaces() {
        return canStartRaces;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof WrenchItem) {
            if (!level().isClientSide) {
                canStartRaces = !canStartRaces;
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        canStartRaces ? "message.ctrcraft.kart.races_on" : "message.ctrcraft.kart.races_off"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.DyeItem dye) {
            // a dye in the hand paints the kart: the tub and struts, or the seat when sneaking
            if (!level().isClientSide) {
                int rgb = dye.getDyeColor().getTextureDiffuseColor();
                if (player.isSecondaryUseActive()) setSeatColour(rgb);
                else setColour(rgb);
                if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /** Where the rider sits, relative to the kart: on the seat of the model, 0.07 block behind the origin (the original driver sits 5 units behind the middle), not in the middle. */
    public static final double SEAT_BACK = 0.07;
    /**
     * The seat's height: where the rider's FEET are put, but a player riding is drawn 0.6 lower than that (the vehicle attachment of the player type), and his hips are 0.7 above his
     * feet, so his hips end up at HIP_HEIGHT = 0.28 above the kart's origin (the original driver's waist is 0.56 up, which was too high on the bulky stand-in kart: seated between that and the first try, -0.04).
     */
    private static final double SEAT_HEIGHT = 0.18;
    public static final double HIP_HEIGHT = SEAT_HEIGHT - 0.6 + 0.7;
    /** The top of the rider's head (torso and head are 1.17 above the hips). */
    public static final double HEAD_TOP = HIP_HEIGHT + 1.17;
    /** The rider's shoulder line (the origin of the player model's body), blocks above the kart's origin: 12 model pixels (0.703) above the hips. */
    public static final double SHOULDER_HEIGHT = HIP_HEIGHT + 0.703;

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        double yaw = Math.toRadians(getYRot());
        // Minecraft: forward = (-sin yaw, cos yaw), so back is (sin yaw, -cos yaw)
        return new Vec3(Math.sin(yaw) * SEAT_BACK, SEAT_HEIGHT, -Math.cos(yaw) * SEAT_BACK);
    }

    /**
     * Gets the rider out beside the kart (left, right, behind, ahead; the first with a floor and room), not on top of it:
     * vanilla's default is the top of the kart's box, which leaves the rider's feet inside it.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double d = 1.0; // kart half width 0.35 + player half width 0.3, with room
        double[][] sides = { { 1, 0 }, { -1, 0 }, { 0, -1 }, { 0, 1 } }; // left, right, behind, ahead
        for (double[] side : sides) {
            double x = getX() + lx * side[0] * d + fx * side[1] * d;
            double z = getZ() + lz * side[0] * d + fz * side[1] * d;
            for (int dy = 0; dy >= -1; dy--) { // on the same level, or one block down
                BlockPos below = BlockPos.containing(x, getY() + dy, z);
                Vec3 floor = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), below, false);
                if (floor == null) {
                    continue;
                }
                Vec3 at = new Vec3(x, floor.y, z);
                if (DismountHelper.canDismountTo(level(), passenger, passenger.getLocalBoundsForPose(Pose.STANDING).move(at))) {
                    return at;
                }
            }
        }
        return new Vec3(getX(), getBoundingBox().maxY + 0.1, getZ());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true; // gravity is the kart physics' (or the empty-kart fall in tick())
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) {
            return true;
        }
        if (source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild) {
                spawnAtLocation(ModItems.KART.get());
            }
            discard();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.KART.get());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setDrawnYawAngle(tag.getInt("DrawnYaw"));
        setColour(tag.contains("Colour") ? tag.getInt("Colour") : DEFAULT_COLOUR);
        setSeatColour(tag.contains("SeatColour") ? tag.getInt("SeatColour") : DEFAULT_SEAT_COLOUR);
        canStartRaces = !tag.contains("CanStartRaces") || tag.getBoolean("CanStartRaces");
        items.wumpa = Math.max(0, Math.min(com.ctrcraft.pickup.ItemManager.MAX_WUMPA, tag.getInt("Wumpa")));
        items.held = tag.getString("HeldPowerup");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("DrawnYaw", getDrawnYawAngle());
        tag.putInt("Colour", colour());
        tag.putInt("SeatColour", seatColour());
        tag.putBoolean("CanStartRaces", canStartRaces);
        tag.putInt("Wumpa", items.wumpa);
        tag.putString("HeldPowerup", items.held);
    }
}
