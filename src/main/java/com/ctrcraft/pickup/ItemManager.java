package com.ctrcraft.pickup;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.ModSounds;
import com.ctrcraft.items.HeldItem;
import com.ctrcraft.items.RuleSet;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.track.RaceManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a driver carries: the wumpa count (0-10) and the held power-up, and what touching a pickup does (server side). CTR's rules, from
 * {@code RB_Fruit.c} and {@code RB_Crate.c}: a fruit is +1 wumpa, a fruit crate 5 + (random mod 4), and a power-up crate only starts the roulette
 * (90 frames, 60 game ticks) for a driver who holds nothing and is not already rolling; the crate is used up either way. The item is drawn
 * from the rule set's table when the roulette ends. The wumpa count and the held power-up belong to the <b>kart</b> (saved with it, so getting out and in
 * again, or driving free-roam, keeps them); a race clears them for its entrants when they are put on the grid.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID)
public final class ItemManager {
    private ItemManager() {}

    /** 90 frames at 30 Hz (VehPhysProc: itemRollTimer = 90) in game ticks. */
    public static final int ROLL_TICKS = 60;
    public static final int MAX_WUMPA = 10;
    /** DRIVER_WUMPA_JUICED_COUNT. */
    public static final int JUICED = 10;

    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_TURBO = 1;
    public static final int EFFECT_TURBO_JUICED = 2;
    /** A TNT crate sits on the kart's head (nothing slows the kart: the decomp has no such effect; this only starts the jump counting that shakes it off). */
    public static final int EFFECT_SLOW = 3;
    /** An explosion hit the kart. */
    public static final int EFFECT_BLAST = 4;
    /** Whether the kart already has a rain cloud over it (a second red beaker refreshes it instead of starting one). */
    public static boolean hasCloud(ServerPlayer p) {
        State s = state(p);
        return s != null && s.cloudTicks > 0;
    }

    /** A beaker hit the kart: it spins out. */
    public static final int EFFECT_SPIN = 6;
    /** The red beaker's rain cloud: this plus the RAIN_CLOUD_EFFECT_* number (0 slow, 1 item roll, 2 icy ground, 3 jump lockout). */
    public static final int EFFECT_CLOUD = 10;
    /** rcloud->timeMS = 0x1e00: 7.68 s in game ticks. */
    public static final int CLOUD_TICKS = 154;

    /**
     * RB_RainCloud_Init: a red beaker puts a cloud over the kart. The first one: item roll if the kart holds an item, otherwise slow; a second one restarts the 7.68 s and
     * draws the effect again ((rng % 400) / 100: slow, item roll, icy ground or jump lockout). Slow is not applied yet (the sim has no damaged speed); heavy friction is
     * not drawn by the game's own code either.
     */
    public static void cloud(ServerPlayer p, boolean fresh) {
        State s = state(p);
        if (s == null) return;
        int effect = fresh ? (s.held.isEmpty() ? 0 : 1) : RANDOM.nextInt(4);
        s.cloudTicks = CLOUD_TICKS;
        if (effect == 1 && !s.held.isEmpty() && s.roll == 0) {
            // the held item is swapped for a new one drawn when the cloud is gone
            s.held = "";
            s.fixed = "";
            s.roll = CLOUD_TICKS;
        }
        s.cloudPending = EFFECT_CLOUD + effect;      // a moment after the spin: the client takes one effect at a time
        s.cloudDelay = 4;
    }

    /** The TNT crate was shaken off: no more slowing. */
    public static final int EFFECT_CLEAR = 5;
    /** What being blasted costs in wumpa (VEH_TUMBLE_WUMPA_PENALTY). */
    public static final int BLAST_WUMPA_PENALTY = 3;

    /** The driver's kart loses fruit (CTR: not while the mask is on, which is not built yet). */
    public static void loseWumpa(ServerPlayer p, int count) {
        State s = state(p);
        if (s == null || s.wumpa == 0) return;
        s.wumpa = Math.max(0, s.wumpa - count);
        sync(p, s);
    }

    /** Sends the driver's client an effect to apply to its kart (the client sim is the one that moves it). */
    public static void applyEffect(ServerPlayer p, int effect) {
        State s = state(p);
        if (s == null) return;
        s.effect = effect;
        s.effectSeq++;
        sync(p, s);
        // RB_Hazard_HurtDriver sets damageColorTimer: every racer's HUD portrait of this driver flashes red
        if (effect == EFFECT_SPIN || effect == EFFECT_BLAST) PacketDistributor.sendToAllPlayers(new HitFlashPayload(p.getUUID()));
    }

    /** What a kart carries. {@code wumpa} and {@code held} are saved with the kart; the rest is the roulette and the effect in progress. */
    public static final class State {
        public int wumpa;
        public String held = "";
        int roll;
        String fixed = "";
        int effect;
        int effectSeq;
        int cloudTicks;
        int cloudPending;
        int cloudDelay;
        /** The bomb this kart's driver last fired, while it may still be rolling (the use key detonates it). */
        UUID bomb;

        void clear() {
            cloudTicks = 0;
            bomb = null;
            wumpa = 0;
            held = "";
            roll = 0;
            fixed = "";
            effect = EFFECT_NONE;
        }
    }

    /** The kart each player's client was last told about (so a change of kart, or getting out, is sent once). */
    private static final Map<UUID, UUID> SYNCED = new HashMap<>();
    private static final Random RANDOM = new Random();
    /** The rules every race uses until the rulebook can set them per track. */
    private static final RuleSet RULES = RuleSet.CTR;

    /** The display name of a power-up id ({@code ctrcraft:turbo} is the {@code power.ctrcraft.turbo} entry in the lang file). */
    public static Component nameOf(String id) {
        int colon = id.indexOf(':');
        return Component.translatable("power." + (colon < 0 ? CtrCraftMod.MODID : id.substring(0, colon)) + "." + (colon < 0 ? id : id.substring(colon + 1)));
    }

    /** The state of the kart the player sits in, or null. */
    private static State state(ServerPlayer p) {
        return p.getVehicle() instanceof KartEntity kart ? kart.items() : null;
    }

    /** A race puts its entrants on the grid with nothing: no fruit, no power-up. */
    public static void reset(ServerPlayer p) {
        State s = state(p);
        if (s == null) return;
        s.clear();
        sync(p, s);
    }

    /** A kart reached a pickup. Returns whether the pickup is used up. */
    public static boolean collect(PickupEntity pickup, PickupSpotBlockEntity spot, ServerPlayer driver) {
        State s = state(driver);
        if (s == null) return false;
        ServerLevel level = (ServerLevel) pickup.level();
        switch (pickup.kind()) {
            case FRUIT -> {
                s.wumpa = Math.min(MAX_WUMPA, s.wumpa + 1);
                level.playSound(null, pickup.blockPosition(), ModSounds.FRUIT_PICKUP.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            case FRUIT_CRATE -> {
                s.wumpa = Math.min(MAX_WUMPA, s.wumpa + 5 + RANDOM.nextInt(4));
                crateBreak(level, pickup);
            }
            case POWERUP_CRATE -> {
                if (s.held.isEmpty() && s.roll == 0) {
                    s.roll = ROLL_TICKS;
                    s.fixed = spot.itemId();         // the roulette's sound is the driver's client's (RouletteSound): a loop for as long as it runs
                }
                crateBreak(level, pickup);
            }
        }
        sync(driver, s);
        return true;
    }

    private static void crateBreak(ServerLevel level, PickupEntity pickup) {
        level.playSound(null, pickup.blockPosition(), ModSounds.CRATE_BREAK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, pickup.getX(), pickup.getY() + 0.5, pickup.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
    }

    /** The use key. */
    public static void use(ServerPlayer p, boolean forward, boolean back) {
        State s = state(p);
        if (s == null) return;
        // a bomb of this driver is still rolling: the key detonates it (VehPhysProc: instBombThrow, tw->flags |= 2), before anything else the key does
        if (s.bomb != null) {
            Entity rolling = p.serverLevel().getEntity(s.bomb);
            s.bomb = null;
            if (rolling instanceof BombEntity bomb && !bomb.isRemoved()) {
                bomb.detonate();
                return;
            }
        }
        if (s.held.isEmpty() || s.roll > 0) return;
        if (s.held.equals(HeldItem.TURBO.id())) {
            // VehPickupItem_ShootNow: VehFire_Increment(0x960, ..., 0x80), 0x100 with ten wumpa
            s.effect = s.wumpa >= JUICED ? EFFECT_TURBO_JUICED : EFFECT_TURBO;
            s.effectSeq++;
            s.held = "";
            sync(p, s);
        } else if (s.held.equals(HeldItem.POTION.id())) {
            // VehPickupItem_ShootNow, beaker: a green one, a red one with ten wumpa; dropped behind, or thrown forward with the forward key held
            KartEntity kart = (KartEntity) p.getVehicle();
            PotionEntity potion = com.ctrcraft.ModEntities.POTION.get().create(p.serverLevel());
            if (potion != null) {
                double yaw = Math.toRadians(kart.getYRot());
                potion.setRed(s.wumpa >= JUICED);
                potion.setOwner(p.getUUID());
                potion.setYRot(kart.getYRot());
                if (forward) {
                    potion.setPos(kart.getX() - Math.sin(yaw) * 1.2, kart.getY() + 0.6, kart.getZ() + Math.cos(yaw) * 1.2);
                    potion.throwFrom(-Math.sin(yaw), Math.cos(yaw));
                } else {
                    potion.setPos(kart.getX() + Math.sin(yaw) * 1.6, kart.getY() + 0.1, kart.getZ() - Math.cos(yaw) * 1.6);
                }
                p.serverLevel().addFreshEntity(potion);
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.MINE_DROP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            s.held = "";
            sync(p, s);
        } else if (s.held.equals(HeldItem.BOMB.id())) {
            // VehPickupItem_ShootNow, WEAPON_ID_BOMB_MISSILE: rolls forward, or backward (back key held, 0.3 of the speed); bigger blast with ten wumpa
            if (!BombEntity.canLaunch()) return;          // ACTIVE_MISSILE_LIMIT: the item is kept
            KartEntity kart = (KartEntity) p.getVehicle();
            BombEntity bomb = com.ctrcraft.ModEntities.BOMB.get().create(p.serverLevel());
            if (bomb != null) {
                bomb.launch(p, kart, s.wumpa >= JUICED, back);
                p.serverLevel().addFreshEntity(bomb);
                s.bomb = bomb.getUUID();
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.BOMB_LAUNCH.get(), SoundSource.PLAYERS, 3.5F, 1.0F);       // SOUND_BOMB_LAUNCH 0x47
            }
            s.held = "";
            sync(p, s);
        } else if (s.held.equals(HeldItem.TNT.id())) {
            // VehPickupItem_ShootNow, TNT: the crate is dropped behind the kart; with ten wumpa it is a Nitro crate
            KartEntity kart = (KartEntity) p.getVehicle();
            double yaw = Math.toRadians(kart.getYRot());
            TntEntity tnt = com.ctrcraft.ModEntities.TNT.get().create(p.serverLevel());
            if (tnt != null) {
                tnt.setNitro(s.wumpa >= JUICED);
                tnt.setOwner(p.getUUID());
                tnt.setYRot(kart.getYRot());
                tnt.setPos(kart.getX() + Math.sin(yaw) * 1.9, kart.getY() + 0.1, kart.getZ() - Math.cos(yaw) * 1.9);
                p.serverLevel().addFreshEntity(tnt);
                p.serverLevel().playSound(null, p.blockPosition(), ModSounds.MINE_DROP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);        // SOUND_MINE_DROP
            }
            s.held = "";
            sync(p, s);
        } else {
            // not built yet: it is used up all the same, so the roulette and the icons can be tested one after another
            p.displayClientMessage(Component.translatable("message.ctrcraft.item.not_built", nameOf(s.held)), true);
            s.held = "";
            sync(p, s);
        }
    }

    private static void sync(ServerPlayer p, State s) {
        PacketDistributor.sendToPlayer(p, new ItemStatePayload(s.wumpa, s.held, s.roll, s.effect, s.effectSeq));
    }


    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = state(p);
            if (s == null) {
                // out of the kart: the HUD goes blank (the kart keeps what it carries)
                if (SYNCED.remove(p.getUUID()) != null) PacketDistributor.sendToPlayer(p, ItemStatePayload.EMPTY);
                continue;
            }
            UUID kart = p.getVehicle().getUUID();
            if (!kart.equals(SYNCED.put(p.getUUID(), kart))) {
                // got in: show what this kart carries, but not the last effect it was hit by (the kart keeps its state, and the client would play that effect again:
                // the white flash and the sound of a beaker, a spin...)
                s.effect = EFFECT_NONE;
                s.effectSeq = 0;
                sync(p, s);
            }
            if (s.cloudTicks > 0) s.cloudTicks--;
            if (s.cloudDelay > 0 && --s.cloudDelay == 0) applyEffect(p, s.cloudPending);
            if (s.roll > 0 && --s.roll == 0) {
                if (!s.fixed.isEmpty()) {
                    s.held = s.fixed;
                } else {
                    RaceManager.Standing st = RaceManager.standing(p);
                    s.held = st == null ? RULES.powerups().draw(1, 0, 1, RANDOM) : RULES.powerups().draw(st.field(), st.rank(), st.lapIndex(), RANDOM);
                }
                // VehPhysProc: the item is announced with OtherFX 0x5E, or 0x41 for a kart with ten wumpa
                p.serverLevel().playSound(null, p.blockPosition(), s.wumpa >= JUICED ? ModSounds.ITEM_GET_JUICED.get() : ModSounds.ITEM_GET.get(),
                        SoundSource.PLAYERS, 0.8F, 1.0F);
                sync(p, s);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SYNCED.remove(event.getEntity().getUUID());
    }
}
