package com.ctrcraft.client;

import com.ctrcraft.ModSounds;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.kart.KartFlags;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The tyres' squeal: a loop that is silent until a skid flag is up (a powerslide, the brake at speed, wheelspin, off ice). */
public class KartSkidSound extends AbstractTickableSoundInstance {
    private final KartEntity kart;

    public KartSkidSound(KartEntity kart) {
        super(ModSounds.SKID_LOOP.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.kart = kart;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    @Override
    public boolean canStartSilent() {
        return true; // it starts at volume 0 and fades in when a tyre skids
    }

    @Override
    public void tick() {
        if (kart.isRemoved() || kart.getPassengers().isEmpty()) {
            stop();
            return;
        }
        this.x = kart.getX();
        this.y = kart.getY();
        this.z = kart.getZ();
        int flags = kart.getVisualFlags();
        boolean skid = KartFlags.has(flags, KartFlags.BACK_SKID) || KartFlags.has(flags, KartFlags.FRONT_SKID);
        float target = skid ? (KartFlags.has(flags, KartFlags.DRIFTING) ? 0.55F : 0.4F) : 0.0F;
        // fade, so it does not click on and off
        this.volume += (target - this.volume) * 0.35F;
        this.pitch = KartFlags.has(flags, KartFlags.DRIFTING) ? 1.0F : 0.85F;
    }
}
