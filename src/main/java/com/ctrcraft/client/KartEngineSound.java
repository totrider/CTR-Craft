package com.ctrcraft.client;

import com.ctrcraft.ModSounds;
import com.ctrcraft.kart.KartEntity;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The engine, a loop whose rate and volume follow CTR's EngineSound_Player values (the kart's synced engine data): the sample
 * plays at 2^((distortion - 128) / 64) of its own rate, which Minecraft clamps to 0.5..2.
 */
public class KartEngineSound extends AbstractTickableSoundInstance {
    private final KartEntity kart;

    public KartEngineSound(KartEntity kart) {
        super(ModSounds.ENGINE_LOOP.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.kart = kart;
        this.looping = true;
        this.delay = 0;
        update();
    }

    /** Without this Minecraft drops the loop for good when the master volume is turned to 0, and it never comes back. */
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
        update();
    }

    private void update() {
        this.x = kart.getX();
        this.y = kart.getY();
        this.z = kart.getZ();
        int engine = kart.getEngineData();
        int distortion = engine & 0xFF;
        int volume = (engine >> 8) & 0xFF;
        this.pitch = Mth.clamp((float) Math.pow(2.0, (distortion - 128) / 64.0), 0.5F, 2.0F);
        this.volume = 0.75F * volume / 255.0F;
    }
}
