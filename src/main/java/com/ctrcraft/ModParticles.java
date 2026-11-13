package com.ctrcraft;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The kart's own particles. */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, CtrCraftMod.MODID);

    /** The dust puff of tyre smoke and backfires: five pictures, bright to dark, shown over the puff's life (CTR's dustpuff00-04). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST_PUFF = PARTICLES.register("dust_puff", () -> new SimpleParticleType(false));

    /** A clod of dirt thrown up by a wheel on dirt (CTR's clod sprite). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOD = PARTICLES.register("clod", () -> new SimpleParticleType(false));
    /** A spark flash (CTR's smokering sprite), used for the sparks of a kart rubbing a wall. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKE_RING = PARTICLES.register("smoke_ring", () -> new SimpleParticleType(false));

    /** The starburst of a bomb exploding (the shared pack's spiky burst picture, tinted yellow to red); its size is passed as the particle's x velocity. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BURST = PARTICLES.register("burst", () -> new SimpleParticleType(false));

    /** A crate breaking: the pieces of the original's break-up animation (client/CrateBreakParticle); its x velocity is the turn about the vertical axis in degrees. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRATE_BREAK = PARTICLES.register("crate_break", () -> new SimpleParticleType(false));

    private ModParticles() {}

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
