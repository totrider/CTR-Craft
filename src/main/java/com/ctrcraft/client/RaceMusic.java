package com.ctrcraft.client;

import java.util.HashMap;
import java.util.Map;

import com.ctrcraft.CtrCraftMod;
import com.ctrcraft.track.RaceClient;
import com.ctrcraft.track.RacePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The race music, as the original plays it (HOWL_AudioState.c Audio_SetState, HOWL_Music.c): a jingle while the start lamps count down (AUDIO_RACE_INTRO), the level
 * music from GO to the finish, a jingle on the last lap during which the music is turned down (Music_LowerVolume: to 90 of 190, then back up), and a jingle at the
 * finish, a different one for first place and for the rest (AUDIO_RACE_END: XA 4 and 5).
 *
 * <p>On the last lap the original also speeds the level music up: once the jingle has played for a second it adds 20 beats per minute to the song's tempo
 * (HOWL_AudioState.c, Audio_SetMaskSong(20)) for the rest of the race, while the jingle plays at its own speed over it. The level music is rendered audio here, so the speed
 * can only be changed together with the pitch ({@link #FINAL_LAP_SPEED}, about +20 bpm at the usual 170 to 190 bpm of the songs).
 *
 * <p>Nothing is shipped: the mod only plays what a resource pack puts into these sound events (all in the CTR Craft namespace, in the music category of the sound
 * options). Without any, nothing plays and nothing is logged. A pack adds its files to {@code assets/ctrcraft/sounds.json}; several sounds in one event are
 * picked at random, again at every repeat of the race music. See docs/MUSIC.md.
 * <pre>
 *   music.race_intro        the lamps are counting down
 *   music.race              from GO until the finish, repeated for as long as the race lasts
 *   music.final_lap         the last lap begins (the race music is turned down while it plays)
 *   music.race_end.first    the driver finished first
 *   music.race_end.other    the driver finished, in any other place
 * </pre>
 * The sounds of the race music should be {@code "stream": true} in sounds.json; a streamed sound cannot loop in the sound engine, so it is started again when it ends
 * (a short gap; a short non-streamed loop repeats without one).
 */
public final class RaceMusic {
    private RaceMusic() {}

    public static final String RACE_INTRO = "music.race_intro";
    public static final String RACE = "music.race";
    public static final String FINAL_LAP = "music.final_lap";
    public static final String RACE_END_FIRST = "music.race_end.first";
    public static final String RACE_END_OTHER = "music.race_end.other";

    /** The music's level while a jingle plays over it: the original lowers it to 90 of its usual 190. */
    private static final float DUCKED = 90.0F / 190.0F;

    /** The speed (and pitch) of the level music on the last lap: the original's +20 bpm on songs of 150 to 230 bpm is 1.09 to 1.13. */
    private static final float FINAL_LAP_SPEED = 1.11F;
    /** The original raises the tempo when the jingle has played for a second: game ticks. */
    private static final int FINAL_LAP_DELAY = 20;

    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
    private static float speed = 1.0F;
    private static int speedDelay = -1;

    private static int lastPhase = RacePayload.OFF;
    private static int lastLap;
    private static Instance loop;
    private static Instance jingle;
    private static int loopAge;
    private static int seenFinish;
    private static Instance endJingle;
    private static int endAge;
    /** The finish jingle plays in full, then fades out once it has gone on this long (30 seconds): game ticks, and the fade's ease per tick. */
    private static final int END_FADE_AFTER = 600;

    /** The music's own sound instance: not positional, volume eased towards a target (the duck while a jingle plays). */
    private static final class Instance extends AbstractTickableSoundInstance {
        float target = 1.0F;
        float targetPitch = 1.0F;
        float ease = 0.25F;

        Instance(SoundEvent event, boolean level) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.looping = false;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 1.0F;
            this.pitch = level ? speed : 1.0F;        // a jingle plays at its own speed
            this.targetPitch = this.pitch;
        }

        @Override
        public void tick() {
            volume += (target - volume) * ease;
            pitch += (targetPitch - pitch) * 0.15F;
        }
    }

    private static SoundEvent event(String path) {
        return EVENTS.computeIfAbsent(path, p -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, p)));
    }

    /** True when the loaded resource packs put at least one sound into the event. */
    private static boolean hasSounds(Minecraft mc, SoundEvent event) {
        var sounds = mc.getSoundManager().getSoundEvent(event.getLocation());
        return sounds != null && sounds.getSound(RandomSource.create()) != SoundManager.EMPTY_SOUND;
    }

    private static boolean warned;

    private static Instance play(Minecraft mc, String path) {
        SoundEvent event = event(path);
        if (!hasSounds(mc, event)) return null;
        if (!warned && mc.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F && mc.player != null) {
            warned = true;                                  // the Music slider is also the race music's: at 0 it plays nothing
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.ctrcraft.music.muted"), false);
        }
        Instance instance = new Instance(event, path.equals(RACE));
        mc.getSoundManager().play(instance);
        return instance;
    }

    private static void stop(Minecraft mc, Instance instance) {
        if (instance != null) mc.getSoundManager().stop(instance);
    }

    private static boolean active(Minecraft mc, Instance instance) {
        return instance != null && mc.getSoundManager().isActive(instance);
    }

    /** Every client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stop(mc, loop);
            stop(mc, jingle);
            loop = null;
            jingle = null;
            lastPhase = RacePayload.OFF;
            lastLap = 0;
            warned = false;
            endJingle = null;
            seenFinish = RaceClient.finishSeq();
            return;
        }
        RacePayload s = RaceClient.state();
        int phase = s.phase();
        // the driver finished: the win or the lose jingle (the state can already be OFF again by the time this looks, so it is counted in RaceClient)
        if (RaceClient.finishSeq() != seenFinish) {
            seenFinish = RaceClient.finishSeq();
            stop(mc, loop);
            stop(mc, jingle);
            loop = null;
            jingle = play(mc, RaceClient.finishPlace() == 1 ? RACE_END_FIRST : RACE_END_OTHER);
            endJingle = jingle;
            endAge = 0;
        }
        if (endJingle != null) {
            if (!active(mc, endJingle)) {
                endJingle = null;
            } else if (++endAge > END_FADE_AFTER) {
                endJingle.target = 0.0F;
                endJingle.ease = 0.012F;
                if (endJingle.getVolume() < 0.02F) {
                    stop(mc, endJingle);
                    endJingle = null;
                }
            }
        }
        if (phase != lastPhase) {
            switch (phase) {
                case RacePayload.GATHER -> {
                    stop(mc, loop);                         // a new race is gathering: the last one's music is over
                    stop(mc, jingle);
                    loop = null;
                    endJingle = null;
                }
                case RacePayload.LIGHTS -> {
                    stop(mc, loop);
                    stop(mc, jingle);
                    loop = null;
                    lastLap = 0;
                    speed = 1.0F;
                    speedDelay = -1;
                    jingle = play(mc, RACE_INTRO);
                }
                case RacePayload.RACING -> {
                    if (lastPhase == RacePayload.LIGHTS) {
                        loop = play(mc, RACE);
                        loopAge = 0;
                    }
                }
                case RacePayload.DONE -> { }          // the finish jingle is started above
                case RacePayload.OFF -> {
                    stop(mc, loop);                         // a finish jingle still plays out
                    loop = null;
                }
                default -> { }
            }
            lastPhase = phase;
        }

        if (phase == RacePayload.RACING) {
            if (s.laps() >= 2 && s.lap() == s.laps() && lastLap < s.laps()) {
                stop(mc, jingle);
                jingle = play(mc, FINAL_LAP);
                speedDelay = FINAL_LAP_DELAY;
            }
            lastLap = Math.max(lastLap, s.lap());
            // the race music goes round again when it has ended (a short grace after the start, while the engine takes the sound up)
            loopAge++;
            if (loopAge > 10 && loop != null && !active(mc, loop)) {
                loop = play(mc, RACE);
                loopAge = 0;
            } else if (loop == null && loopAge > 40 && hasSounds(mc, event(RACE))) {
                loop = play(mc, RACE);                      // a pack that was switched on during the race
                loopAge = 0;
            }
        }
        if (speedDelay > 0 && --speedDelay == 0) speed = FINAL_LAP_SPEED;        // from here on, also for every repeat of the music
        if (loop != null) {
            loop.targetPitch = speed;
            loop.target = active(mc, jingle) ? DUCKED : 1.0F;
            if (loopAge % 20 == 0) mc.getMusicManager().stopPlaying();       // the world's own music stays out of the race
        }
    }
}
