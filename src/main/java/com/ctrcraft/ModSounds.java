package com.ctrcraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events; the samples are original, synthesised by tools/make_sounds.py (CTR's recordings are not ours to ship). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CtrCraftMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_LOOP = reg("engine_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKID_LOOP = reg("skid_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBO_LOOP = reg("turbo_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOP = reg("hop");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND = reg("land");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_START = reg("drift_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOST = reg("boost");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOST_FAIL = reg("boost_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> METER_RED = reg("meter_red");
    public static final DeferredHolder<SoundEvent, SoundEvent> METER_FULL = reg("meter_full");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANG_TIME = reg("hang_time");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALL_HIT = reg("wall_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = reg("slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIN_OUT = reg("spin_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_COUNT = reg("light_count");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_GREEN = reg("light_green");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_PICKUP = reg("fruit_pickup");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRUIT_ARRIVE = reg("fruit_arrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLASS_SHATTER = reg("glass_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_DROP = reg("mine_drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TNT_HIT = reg("tnt_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> TNT_LAND = reg("tnt_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> TNT_TICK = reg("tnt_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> TNT_EXPLODE = reg("tnt_explode");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRATE_BREAK = reg("crate_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_ROLL = reg("item_roll");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_GET = reg("item_get");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_GET_JUICED = reg("item_get_juiced");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_SOFT = reg("crash_soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_MID = reg("crash_mid");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_HARD = reg("crash_hard");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
