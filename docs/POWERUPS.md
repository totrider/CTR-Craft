# Power-ups: what the original has and what the mod does

Source of the list: the Crash Team Racing weapons and power-ups table on StrategyWiki (checked against the decomp's `HELD_ITEM_*` ids in `HeldItem`). A power-up has a normal
effect and a **juiced up** form, which a kart gets with 10 Wumpa fruit (`ItemManager.JUICED`). The roulette tables are in `ItemTables`; the design of the system is in
[DESIGN_ITEMS.md](DESIGN_ITEMS.md). Status: only the Turbo is built (`ItemManager.use`); the others are used up with a "not built yet" message.

| Power-up (`HeldItem`) | Normal effect | Juiced up | Notes for the mod |
|---|---|---|---|
| **Tracking Missiles** (`missile`, `missile_3x`) | One or three. Home in on the closest kart ahead; a hit explodes the kart and stops it for a few moments. A warning target shows behind you when one is chasing you. Dodge by steering sharply or by releasing a rear-firing weapon. | Faster and track more precisely. | Needs kart targeting, a projectile entity, a warning cue. |
| **Bowling Bombs** (`bomb`, `bomb_3x`) | Fly in a straight line, thrown forward or backward; explode on an obstacle and stop karts in the blast radius. Can be detonated early with the use key again. | Bigger blast radius, easier to hit several karts. | Projectile entity; second press of the use key detonates. |
| **Power Shield** (`shield`) | Green shield protects once, for a few seconds, from any attack or obstacle, then disappears. Can be thrown forward like a bomb with the use key. | Turns blue and stays active until you are attacked or hit something dangerous. | A flag on the kart; thrown form is a bomb-like projectile. |
| **Explosive Crate / TNT** (`tnt`) | Dropped behind you. A kart that hits it is slowed, and it explodes after three seconds, stopping them. Can be got rid of by jumping repeatedly and quickly. | Becomes a Nitro crate: explodes at the slightest contact. | Look: the Minecraft TNT block as the base for the sprite and the placed entity, altered slightly so it reads as a power-up and not a real TNT block (decided with the author). |
| **N. Brio's Beaker** (`potion`) | Dropped green beaker; explodes on contact and stops the kart. Can be thrown forward by pressing the use key together with forward. | Turns red; also slows any kart that hits it for a few seconds. | Dropped/thrown entity. |
| **Turbo** (`turbo`) | A speed burst. | A bigger speed burst. | **Built**: fire level 128, 256 with ten Wumpa (`VehPickupItem_ShootNow`). |
| **Warp Orb** (`warpball`) | Reaches the kart in first place and stops it for a few moments. | Stops ALL karts in front of you. | Needs race order (`RaceManager.ranking`). |
| **Aku Aku / Uka Uka Mask** (`mask`) | Invincible for a few seconds; faster, invulnerable to attacks, hits opponents by contact. | Lasts longer. | Mask songs 1 and 2 of the level's song pack play while it is on (see [MUSIC.md](MUSIC.md)). |
| **N. Tropy Clock** (`clock`) | Briefly immobilises every kart but yours, then slows them for a few seconds. Very powerful, rare. | Effect lasts longer. | Rare in the tables. |
| **Super Engine** (`super_engine`) | **Battle Mode only**: more speed for a few seconds. | Lasts longer. | Only in the battle table (`BATTLE_DEFAULT`); not used by the race rule set. |
| **Invisibility** (`invisibility`) | **Battle Mode only**: invisible to others (not invulnerable) for a few seconds. | Lasts longer. | Only in the battle table. |

## Not in the original's list
`HeldItem.SPRING` (id 5) exists in the decomp's enum and no table draws it: cut content, kept only for the ids. It should not be offered in any rule set by default.

## Decisions so far
- Fidelity: the original's items and tables are the default rule set; anything else is a rule-set choice (see the fidelity policy in `CLAUDE.md`).
- The race roulette draws only the eleven items above (not Super Engine or Invisibility, which are battle-only).
- Kart-versus-Minecraft-entity effects of the weapons follow the entity-interaction policy in `CLAUDE.md` (on by default, tunable by config).
- Icons: the originals (extracted locally from the user's disc) are 48 x 32; the mod ships none and shows the power-up's name without a pack.

## Built: TNT and Nitro crate (2026-11-13)
`TntEntity` (+ `TntRenderer`): the use key drops the crate 1.9 blocks behind the kart (a Nitro crate when the kart has ten wumpa). The first other kart to touch it (the dropper is spared for 1.25 s) is slowed to a third of its class speed (`KartSim.itemSlow`) while the crate sits on its head and flashes, and three seconds later it explodes and spins that kart out (`itemBlast`); a Nitro crate explodes at the first touch. The explosion also throws and hurts other entities (vanilla explosion without block damage). The look is the Minecraft TNT block, 0.8 scale, flashing on a kart, green for Nitro. **Not done:** getting rid of the crate by jumping repeatedly, the wumpa lost by the victim, a crate hit by something other than a kart, the original's sounds and sprite for the crate.

### TNT details checked against the decomp (2026-11-13)
- **Slowdown:** none found. Nothing in the vehicle code reads the "TNT on head" pointer, damage type NONE does nothing, `RB_TNT.c` has no speed code. The manual's "slows you down" may be a feel from hopping (a kart in the air cannot accelerate), so the mod has none.
- **Jumps:** counted only once the crate has landed on the head (not during its flight onto it), one per jump that *starts* (`ACTION_JUMP_STARTED`). On landing `jumpsRemaining = 8 - (rng % 2)` with a signed rng: 9, 8 or 7 (a quarter, a half, a quarter); each counted jump uses one up and the next jump after zero throws the crate off, so 8, 9 or 10 jumps in the 90 frames (3 s) of the fuse. The mod counts them on the server (`TntEntity.hop`) the same way.
- **Safety frames:** the dropper is spared for 10 frames (`MINE_PARENT_SAFETY_FRAMES`); everybody else is hit at once, within 120 lu (2.4 blocks) of the crate.
- **Blasted animation:** `VehStuckProc_Tumble_Animate` plays frame `(time left / 32 ms) mod 27` of the table `Vector.c` generates (roll 2 turns, pitch swing up to 51 degrees, squash/stretch); ported in `KartRenderer`.

## Built: N. Brio's Beaker (2026-11-13)
`PotionEntity` (+ `PotionRenderer`, a tinted vanilla potion bottle until the original model is extracted): the use key drops it behind the kart, or throws it forward in an arc when the forward key is held (`POTION_THROW_FORWARD`; speed 0x78 lu/frame, up 0x30, gravity 4 lu/frame, a wall bursts it, the ground lets it stay). Another kart than the dropper's (spared 10 frames) whose centre comes within 80 lu (1.6 blocks) spins out (`damage type SPIN`) and the beaker bursts with the glass sound (0x3F). A kart with ten wumpa throws a red beaker, which also puts a rain cloud over the victim for 7.68 s (`RB_RainCloud_Init`): item roll if it holds an item (the item is swapped for a new one when the cloud goes), else slow; a second red beaker restarts it with a random effect. **Applied:** item roll, icy ground, jump lockout. **Not yet:** the slow effect (the sim has no "damaged speed" yet), the rain cloud model and rain, heavy friction (the game's own cloud code never draws it), the beaker model and its flash effect, the pool limit of mines.

## The original's weapon models (2026-11-13)
They are in the shared model pack (`BIGFILE` entry 259, the same file as the icons): `crate_tnt`, `crate_nitro`, `crate_explosion`, `bomb`, `beakergreen`, `beakerred`, `warpball`, `cloud`, `shockwavered`, `shockwavegreen`, `shield`, `shielddark`, `shield_green`, `warpedburst`, `akumouth`, `akubeam`, `token`; the missile is not among the names found. `tools/ctr_disc_textures.py` `pack_models` lists them; `tools/export_ctr_mesh.py --pack` writes OBJ files to `local-assets/ctr-mesh/` (local only). The models with a single frame (the crates, bomb, shield, akubeam, token) export; the ones with compressed animation frames (the beakers, warp orb, cloud, shockwaves, warped burst, akumouth) are skipped until the frame deltas are decoded.
`tools/render_ctr_model.py` is a small software renderer (textures, palettes, vertex shading) that draws any of them from any angle. The TNT and Nitro crates are built from it: the pack makes a one-block cube with the model's own side, top and bottom pictures (`extract_ctr_textures.py`). For models that are not boxes the plan is directional sprites: pre-render a ring of views (a few pitches) and let the renderer pick the one that matches the angle the object is seen from, as the Wumpa fruit's spin does.
The weapon crate (`crate_question`) and the fruit crate (`crate_fruit`) are built the same way since 2026-11-13: the real models of Crash Cove's level file, rendered from the side, top and bottom with the game's vertex shading, one cube per pack with those pictures.

## Placed weapons: the pool and their life (2026-11-13)
- **Original** (`RB_MinePool.c`): one pool of **10** mines (TNT, Nitro, beakers, from every driver) for the race; a new one when it is full makes the oldest burst; one that is hit, or lands on a head, leaves the pool; nothing else times them out, they end with the race.
- **Mod** (`MinePool`): *in a race* one pool of 10 for the race, cleared when the race ends; *in free roam* a pool of 10 for each player, and a weapon nobody touches goes away after five minutes (`FREE_ROAM_LIFETIME_TICKS`). They are not saved with the world: unloaded chunks drop them.
- **Beakers:** green and red both spin the kart out (damage type SPIN, `VehPickState`), costing a wumpa; only an explosion (TNT, Nitro) is a blast with the tumble and the 3 wumpa. A red beaker adds the rain cloud a moment after the spin.
- **Hang time:** a blasted kart gets no hang-time boost from its bounces (`VehFire_Increment` refuses KS_SPINNING, KS_BLASTED and KS_MASK_GRABBED).
