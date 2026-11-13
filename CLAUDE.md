# Craft Team Racing - project rules

NeoForge 1.21.1 (21.1.265) mod, Java 21, Parchment mappings. Mod id `ctrcraft`, package `com.ctrcraft`.
Roadmap: `docs/ROADMAP.md`. Research: `docs/RESEARCH.md`.

## Workflow
- Test instances: `F:\ATLauncher\instances\CTRCraft` (NeoForge 21.1.265) and `F:\ATLauncher\instances\CTRCraftMP` (multiplayer tests;
  every build goes to both). `build-and-install.bat` builds and copies the
  jar into its `mods` folder. Minecraft locks the jar while it runs: close the game first.
- Models: **Blockbench first**. Every block/item/entity model's geometry lives in `blockbench/<family>/*.bbmodel` and is
  the source of truth; generators in `tools/` read them. Never put geometry only in Java or hand-edit generated model
  JSON. Patch existing bbmodels in place so the user's manual edits survive. New model = new bbmodel first.
- Textures: layered Krita `.kra` in `art/` (not .ora); a build script exports PNGs. Never hand-edit generated PNGs.
- When handing over an editable source file (bbmodel, kra, config), say up front anything non-obvious about its structure.
- `.mcp.json` is local only (gitignored). The Blockbench MCP server is configured there (local path).
- Datagen output in `src/generated/resources`; data-driven things (terrain table, recipes, tags) as data packs where possible.

## Code rules
- `com.ctrcraft.physics` must not import Minecraft or NeoForge classes. It uses CTR's integer fixed-point math exactly
  (see RESEARCH.md conventions). Anything Minecraft-specific goes through an interface in that package.
- Port from ctr-native / world-of-kartcraft's spec function by function; note the C source function in a comment and in
  `docs/PHYSICS_PORT.md`. Verify constants against the raw files, never from memory.
- Sable/Create are soft dependencies (compile-only + `compat/` package + optional mod dependency). The mod must load
  without them. Never bundle Sable (Polyform Shield).
- Do not ship CTR game assets (models, textures, audio). License is GPL-3.0-or-later because the physics is a port.
  Original CTR sounds and textures only ever exist as LOCAL resource packs made from the user's own disc by `tools/extract_ctr_sounds.py` and
  `tools/extract_ctr_textures.py` (`extract-ctr-sounds.bat`, `extract-ctr-textures.bat`; output in git-ignored `local-assets/`, installed into the instance's `resourcepacks/`, never in the jar,
  never uploaded). The disc image in the project folder is git-ignored too. The mod ships synthesised placeholders (`tools/make_sounds.py`).
- Client-side sim at fixed 30 Hz with interpolation; server validates, does not simulate.

## Fidelity policy (decided 2026-11-12)
- **Default behaviour is the PS1 game's**, as read from the decomp (ctr-native), not from later games (Nitro-Fueled etc.) and not from
  memory. A constant or rule that is not in the decomp is not "CTR": say so, and make it an extension.
- **Extensions are opt-in, by what you build or choose**: a different block (e.g. a Super Boost Pad family), a different item, a datapack
  entry or a config flag; never a silent change to the default. Because tracks are built from blocks, an extension is just another block.
- **Exception, decided by the author: interaction with Minecraft entities** (mobs, boats, minecarts, players on foot; damage, knockback,
  power-ups hitting them) is core to the survival gameplay and is **on by default**. It is a separate system from the race mechanics and
  never changes the CTR kart-vs-kart physics; tune it with config flags rather than switching it off by default.
- When unsure what PS1 does, read the decomp before answering or coding: the full ctr-native source is in `reference/ctr-native-src/` (git-ignored;
  `game/Vehicle/*.c`, `game/DotLights.c`, `game/zGlobal_DATA.c` for the stat tables...); `reference/ctr-native/` is an older partial copy.

## Privacy and scope (this repository is public)
- **Never mention, link or name any other project, repository, folder or work of the author's** (private or public, finished or not), or any
  personal detail (real name, handles, e-mail, user names, personal interests, machine paths beyond the generic test-instance path), anywhere in
  the repository: code, comments, docs, README, lang files, tool scripts, configs, commit messages, tags or release notes.
- If a rule, idea or setting was carried over from elsewhere, state the rule on its own, never where it came from ("same as <other project>" is not allowed).
- Public third-party references are fine and expected (ctr-native, world-of-kartcraft, Sable, Create, Fusion, Copycats+, NeoForge ...).
- Before every commit: search the tree (`git grep -i`) and the commit message for names of other projects and personal details, and
  check that `git config user.name` / `user.email` is an identity the author wants public.
