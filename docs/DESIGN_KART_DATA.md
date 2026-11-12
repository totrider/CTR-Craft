# Kart data: tuning (stats) separate from look (visuals)

Status: **design, agreed in principle 2026-11-12, not built.** Follows the fidelity policy in [../CLAUDE.md](../CLAUDE.md): PS1 behaviour by default,
extensions opt-in.

## The split
A kart has two independent pieces of data. Neither one ever changes the other.

| | Tuning profile | Look (visual parts) |
|---|---|---|
| What | the physics stats | chassis, wheels, seat, steering, exhaust... (the model's groups) |
| Decides | accel, top speed, turn rate, turn decrease, turn input delay, drift turn base, collision weight | how the kart is drawn |
| Data component | `ctrcraft:tuning` | `ctrcraft:look` (later) |
| Synced | to the driver (physics runs on their client) and to everyone for display (name) | to everyone (the renderer) |
| Edited with | the Tuning Wrench | a customisation screen / parts items (later) |
| Default | the four PS1 engine classes | the current placeholder kart |

Both live on the **kart item** and on the **kart entity**: spawning copies the item's components to the entity, picking up (or pick-block)
writes them back to the item, so a tuned kart stays tuned when you carry it. (Components, not NBT: NeoForge 1.21.1 `DataComponentType`s.)

## Tuning profile
- `KartClass` (an enum of the four PS1 classes) becomes `KartStats` (a record in `com.ctrcraft.physics`, still no Minecraft imports).
  `KartSim` takes a `KartStats`; the four enum constants remain as the built-in defaults, so nothing about driving changes.
- A profile is a **datapack entry**: `data/<ns>/ctrcraft/kart_tuning/<name>.json` with a display name and the stats. Shipped: `balanced`, `acceleration`,
  `speed`, `turning` with the exact PS1 numbers (spec section 10), flagged `"official": true`. Anyone can add more (clearly not PS1).
- The component stores the profile id (`ctrcraft:balanced`), not copies of numbers, so datapack tweaks apply and old karts stay valid; an
  unknown id falls back to `balanced`.
- Racers are presets by name: "Crash" shows as Balanced etc. (cosmetic label, spec section 10 table).

## Tuning Wrench
- Item `ctrcraft:tuning_wrench`. Right-click a kart (nobody driving it) opens a screen: the available profiles as a list, bars for the stats, an
  "official PS1" badge. Picking one writes the component on the entity. Pick the kart up and the item carries it (tooltip shows the profile).
- **Open:** who may use it (anyone / creative+ops), and whether the screen may offer free numeric stats (default: no; only profiles; a server
  config flag could enable sliders, clearly marked as not PS1).

## Look (later)
- Parts: the model already has one group per part (`chassis`, `nose`, `body`, `seat`, `steering`, four wheels, `exhaust`). A look = one choice
  per slot (a part id), each part a small Blockbench model + texture (same rules as everything else: bbmodel is the source).
- Rendering composes the chosen parts (a baked model per part, drawn in order), still with the wheelie/tilt/flame effects, which attach to anchors, not to the part.
- **No stats from visuals, ever** (the user's rule). If survival crafting (M3) wants "better wheels = better handling", that is a separate opt-in
  rule that *sets a tuning profile*, it is not baked into the part.

## Build order
1. `KartStats` + datapack tuning entries + `ctrcraft:tuning` component on item and entity + tests (physics unchanged for the four defaults).
2. Tuning Wrench + screen.
3. Look component + parts (later, own milestone).
