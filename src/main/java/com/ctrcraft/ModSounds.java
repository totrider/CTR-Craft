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

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
