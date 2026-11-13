package com.ctrcraft.client;

import com.ctrcraft.ModSounds;
import com.ctrcraft.pickup.ItemClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * The power-up roulette's sound: CTR starts OtherFX 0x5D when a weapon crate is broken and it loops until the item is drawn (RB_Crate.c, MainGameEnd.c stops it).
 * A looping sound on the driver's client for as long as the roulette runs, not positional.
 */
public final class RouletteSound extends AbstractTickableSoundInstance {
    private static RouletteSound playing;

    private RouletteSound() {
        super(ModSounds.ITEM_ROLL.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.8F;
    }

    @Override
    public void tick() {
        if (!ItemClient.rolling()) stop();
    }

    /** Every client tick: start the loop when a roulette begins. */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (playing != null && (playing.isStopped() || !mc.getSoundManager().isActive(playing))) playing = null;
        if (playing == null && mc.player != null && ItemClient.rolling()) {
            playing = new RouletteSound();
            mc.getSoundManager().play(playing);
        }
    }
}
