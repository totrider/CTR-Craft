package com.ctrcraft.client;

import com.ctrcraft.ModSounds;
import com.ctrcraft.kart.KartEntity;
import com.ctrcraft.kart.KartFlags;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The turbo's roar: CTR (VehTurbo.c) plays OtherFX 0xE every frame while the reserves burn. A loop, silent otherwise. */
public class KartTurboSound extends AbstractTickableSoundInstance {
    private final KartEntity kart;

    public KartTurboSound(KartEntity kart) {
        super(ModSounds.TURBO_LOOP.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.kart = kart;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
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
        boolean burning = KartFlags.fireSize(flags) > 0;
        // CTR plays it at 0x100 - (alpha >> 4): it dies down with the flame
        float target = burning ? 0.55F * (8 - KartFlags.fade(flags)) / 8.0F : 0.0F;
        this.volume += (target - this.volume) * 0.4F;
        // the game nudges the distortion up while it burns; the bigger the flame the higher it sings
        this.pitch = 0.95F + 0.03F * KartFlags.fireSize(kart.getVisualFlags());
    }
}
