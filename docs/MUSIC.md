# Race music

The mod plays music in a race, but ships none: a resource pack puts the files in. Without a pack nothing plays and nothing is logged. The tracks play in the
Music volume slider of the sound options, and the world's own background music is kept out of the way while the race music plays.

## When it plays

This follows what the original game does (`HOWL_AudioState.c`, `HOWL_Music.c`):

| Sound event (namespace `ctrcraft`) | When | Original |
|---|---|---|
| `music.race_intro` | the start lamps are counting down | race intro jingle (music 0 to 3, in turn) |
| `music.race` | from GO until you finish, repeated for as long as the race lasts | the level's music |
| `music.final_lap` | the last lap begins; the race music is turned down (to 90/190 of its level, the original's figure) while this plays | "Final lap" jingle (music 6) |
| `music.race_end.first` | you finished first | race end, music 4 |
| `music.race_end.other` | you finished in any other place | race end, music 5 |

Not done yet: music that depends on the track (the mod's tracks have no names the music could be keyed to), the Aku Aku / Uka Uka mask songs, the menu music.

## Adding music to a resource pack

Put the sound files (Ogg Vorbis) under `assets/ctrcraft/sounds/` and list them in `assets/ctrcraft/sounds.json`. Packs add to the mod's (empty) lists. Several sounds in one
event are picked at random, again at every repeat of the race music, so a pack can offer a playlist:

```json
{
  "music.race": {
    "sounds": [
      { "name": "ctrcraft:music/my_song", "stream": true },
      { "name": "ctrcraft:music/my_other_song", "stream": true, "weight": 2 }
    ]
  },
  "music.race_end.first": {
    "sounds": [ { "name": "ctrcraft:music/fanfare", "stream": true } ]
  }
}
```

The files are `assets/ctrcraft/sounds/music/my_song.ogg` and so on. Notes:

* Long tracks should be `"stream": true` (they are not loaded into memory). A streamed sound cannot loop inside the sound engine, so the mod starts the race music again when it
  ends: there can be a short gap. A short sound without `stream` loops without a gap but is held in memory.
* The race music is not positional and is at full volume (before the slider); the pack author sets the loudness of the file.
* `"replace": true` on an event replaces what packs lower down added.
* Any player can do this for their own music: a resource pack with just a `pack.mcmeta`, the `sounds.json` above and the files.

## The original's jingles

`extract-ctr-music.bat` (`tools/extract_ctr_music.py`) decodes the original's jingles from your own raw disc image (2352 bytes a sector: the jingles are CD-XA audio, which a cooked
`.iso` does not keep) into the local resource pack `CTRCraft-OriginalMusic` and installs it in the test instances. The pack stays on your machine, never in the repository or
the jar. It fills `music.race_intro` (the four intro jingles), `music.final_lap` and the two `music.race_end` events. A track is located as the game does
(`XA/ENG.XNF`, then the sectors of its channel in `XA/MUSIC/S00.XA`; the decoder follows `platform/native_audio.c`).

## The original's level music (rendered from the sequenced songs)

The original's race music is not recorded: each level has a CSEQ song pack in `SOUNDS/KART.HWL` (the sector offsets follow the bank offsets in the HOWL header,
`howl_ParseHeader`), a small MIDI-like format played at 60 Hz by the game's own sequencer through the instrument and drum samples of the bank, on the SPU's 24 voices.
`tools/ctr_cseq.py` (constants in `tools/ctr_spu_tables.py`, copied from `zGlobal_DATA.c` and `native_audio.c`) parses a pack, runs the sequencer offline and mixes the SPU voices;
`tools/extract_ctr_music.py` renders **song 0 of the pack of each level 0 to 24** (the race tracks and the arenas; `data.levBank_Song` is the identity there) into the local pack
`CTRCraft-OriginalMusic` as `sounds/music/ctr_level_<level>.ogg`, listed under `music.race` (the event picks one at random per repeat, as above). `extract-ctr-music.bat` does it
together with the jingles; `--no-level-music` skips it, `--jobs N` sets the processes (about a minute for all 40 songs). The hub levels (25 to 29), the boss race song and the
menu/intro/credits songs are not rendered. Everything is local (`local-assets/ctr-music`, git-ignored, never in the jar).

**The loop.** Every sequence ends with the end opcode, which restarts it (`boolLoopAtEnd` is 1 for level music, `Music_Adjust`): there is no separate loop marker. The file is the *second*
pass: from the frame the sequences first restart to the frame they restart the second time (one full period, 68 to 128 s depending on the level), so it begins with the tails of the
notes of the previous pass and playing it over and over is the endless loop. The mod restarts a streamed sound when it ends, so the gap between repeats is the one described above.

**The mask songs.** Songs 1 and 2 of each pack are the Aku Aku and Uka Uka songs (full volume, `SongPool_Start`). They use the pack's instrument table, which differs between groups of
levels, so there are nine Aku Aku and six Uka Uka versions (`ctr_aku_aku_<first level using it>.ogg`, `ctr_uka_uka_<...>.ogg`), listed under `music.aku_aku` and `music.uka_uka`.
The mod plays neither yet.

### What is ported, by C function

| Part | Python (`tools/ctr_cseq.py`) | C source |
|---|---|---|
| song pack / song / note header layout | `CseqPack` | `howl_ParseCseqHeader`, `howl_LoadSong` (HOWL_Load.c), `SongPool_Start` (HOWL_SongPool.c) |
| delta time of an event | `get_next_note` | `howl_GetNextNote` (HOWL_Load.c) |
| tempo, 16.16 tick timer, per-sequence event loop, restart | `sequence` | `SongPool_CalculateTempo`, `SongPool_Start`, `Channel_ParseSongToChannels` (HOWL_Channel.c) |
| opcodes 1 (note off), 3 (end), 5 (note on), 6 (volume), 7 (pan), 8 (reverb), 9 (instrument), 10 (pitch bend) | `sequence` | `cseq_opcode01_noteoff` to `cseq_opcode0a` (HOWL_CseqOpcode.c) |
| note on: pitch, ADSR words, volume | `sequence`, `instrument_pitch`, `drum_pitch`, `channel_volume` | `howl_InitChannelAttr_Music`, `howl_InstrumentPitch`, `Channel_SetVolume`, `Channel_AllocSlot` |
| ADSR words to SPU envelope fields | `adsr_fields` | `Channel_UpdateChannels` (HOWL_Channel.c) |
| envelope | `envelope` | `NativeAudio_AdsrAdvance`, `NativeAudio_AdsrRunEnvelopeStep` (platform/native_audio.c) |
| voice: position counter, 4-point gaussian interpolation, loop, end of sample | `interpolate`, `render_voice` | `NativeAudio_InterpolateVoiceSample`, `NativeAudio_MixFrame` |
| ADPCM decode, loop region | `extract_ctr_sounds.decode_adpcm`, `steady_loop` | `NativeAudio_VoiceStreamAdvanceBlock` |
| mixing: voice volume, master volume `0x3FFF`, music volume 175, song volume `0xBE` | `render_voice`, `sequence` | `NativeAudio_ApplyVolume`, `howl_InitGlobals`, `SongPool_Start` |

Voices are the game's 24 channels with its free list (the first free one is taken, a released one goes to the back); a key on a voice that still sounds cuts it.

### Checks (without listening)

`extract_ctr_music.py` writes `music-report.json` next to the pack with these for each of the 40 songs; all pass:

* **Length / tempo**: the rendered loop is within 0.5 frame of the length computed independently from the raw event streams (sum of the delta times up to the end opcode, divided by the
  game's integer tempo per frame, at the NTSC frame rate). The game computes the tempo for 60 Hz but its vertical sync runs at 59.94 Hz, so the music is 0.1 % slower than `bpm` says;
  the render follows that (`FRAME_RATE`).
* **Loop**: the number of note-ons in the first pass equals the number in the second pass for every song. The sequences of a song restart together, except where the data says otherwise
  (hot air skyway has a sequence of twice the length of the others, dragon mines one of 62 frames that loops out of step with the rest).
* **Beat**: the autocorrelation of the onset strength of the audio peaks at the beat period (3.5 to 8.9 times its mean level).
* **Pitch**: one instrument played at notes 57/60/69/72 through the same code gives 109.4 / 130.3 / 218.7 / 260.2 Hz: octaves 1.996 and 1.998 (bin resolution), a minor third 1.191 (2^(3/12) = 1.189).
* **Level**: no clipped sample, DC offset at most 0.002, no silent 100 ms stretch; peaks 0.48 to 0.90 and RMS -24 to -15 dBFS after the one makeup gain below; the energy sits below 2 kHz with
  nearly none above 6 kHz (the samples are low-rate material).
* No note was dropped for lack of voices and no unknown opcode occurs.

The unprocessed mix is quiet (the game's music volume is 175/255 and its sound effects are louder), so every song gets the **same makeup gain** (+11.1 dB, chosen so the loudest song peaks
below 0.9); the levels between songs are the game's.

### What is approximate

Nothing here has been compared with a recording of the real game: it was checked by numbers, not by ear.

* **Reverb is not emulated, because the music does not use it**: a voice sends to the SPU reverb only if its sequence's `reverb` is set, which `SongPool_Start` initialises to 0 and only
  opcode 8 changes, for voices already sounding; none of the 40 songs contains opcode 8 (counted in the report). If a pack ever did, `reverb_opcodes` in the report would be non-zero
  and the render would lack it.
* **Pitch bend** (opcode 10) is implemented as the C does it, but no song uses it, so that path is untested against data. Volume and pan changes do occur and are handled.
* **ADSR**: ported from the decomp's SPU emulation (itself from public SPU documentation), event driven instead of per sample. Every instrument in the packs has the same shape
  (instant exponential attack, no decay, steady sustain, a 4-sample linear release) and drums use the fixed words the game gives them, so in practice only attack and release matter.
  A key off takes effect at the frame boundary, as in the game. The generic phases are only as exact as the decomp's emulation.
* **Interpolation** uses the SPU's gaussian table but in floating point (no per-term truncation, no 16-bit clamp between stages). A looping sample plays its steady-state loop body from the first
  loop jump (what the SPU plays from the second pass on) instead of re-decoding with the running ADPCM history, which differs only in that first pass.
* **Frame timing**: events happen on 60 Hz frames (NTSC 59.94 Hz), so a note starts on a frame boundary as in the game; PAL (50 Hz) is not modelled.
* **Voices**: music has all 24 voices here; in a real race sound effects share them, so the original can drop music notes the render keeps.
* **Volume**: the music volume is the default 175 (the options slider changes it) and the song volume `0xBE` (`0xFF` for the mask songs); the final lap duck is not applied.
* The adventure hubs, boss races and the Naughty Dog crate intro (other volumes, advanced song sets) are not covered.

## The last lap
The original raises the level music's tempo by 20 bpm for the rest of the race once the final-lap jingle has played for a second (`HOWL_AudioState.c`: `Audio_SetMaskSong(20)`), the jingle playing at its own speed over it. The mod does the same with the rendered files by playing them 11 % faster (`RaceMusic.FINAL_LAP_SPEED`, about +20 bpm on songs of 150 to 230 bpm), which also raises the pitch by about two semitones; a true tempo-only change would need the songs re-rendered at the higher tempo (`ctr_cseq.sequence` takes the song's tempo from its bpm) and a way to switch between two files mid-song, which Minecraft's sound engine cannot do seamlessly.
