# Litematica Upgraded code map

Read this before changing code; update it in the same change when files move or are added.
`CodeHygieneTest` (src/test) fails the build when our own code (not masa's) has 6 or more lines in a row twice, or the
same table of 4 or more values twice: make one shared method or table instead.

## What lives where

**masa's code** (LGPL-3.0, not ours - keep changes to it small):
- `src/main/java/com/useleess/litematicaupgraded/litematica/` and `.../malilib/`: Litematica 0.40.1 and malilib 0.60.2
  (`ornithe/1.12.2` branches, MCP names). Copied from `../reference/` by `tools/convert_upstream.py`, which moves the
  packages under `com.useleess.litematicaupgraded` and copies the assets; re-run it after updating the reference.
- `tools/forge-overrides/`: our Forge versions of masa's loader glue (mod entry points, a few `GameWrap` /
  `WorldWrap`-style wrappers, missing lang keys); they replace the upstream file when converting.
  `tools/upstream-exceptions.txt`: loader-only files left out. `tools/upstream_coverage.py` checks nothing else is missing.
- `src/main/resources/mixins.litematica.json`, `mixins.malilib.json`: masa's own mixins.

**Our code**, `src/main/java/com/useleess/litematicaupgraded/`:
- `gui/`: shared screens of our own (`BlockPicker`: pick a block by name, used by the missing-blocks window and Replace).
- `LitematicaUpgraded.java`: the Forge `@Mod` (no hard dependency; `MixinCheck.require()` stops with a clear message when no Mixin is installed); hands everything to the proxy.
- `proxy/`: `ClientProxy` (Litematica, malilib, printer, client side of the server part, the one-time import),
  `CommonProxy` (server options, network channel, server part), `ServerProxy` (dedicated server: common part only).
- `compat/`: `OptifineMixinPlugin`, makes malilib's render hooks optional when OptiFine is installed (also answers Mixin 0.7's
  `preApply`/`postApply` signature, using the compile-only stub in `src/mixin07/`); `MixinCheck` / `ClientError` (no Mixin installed: readable
  error); `InterfaceAccess` (casts to our accessor interfaces, kept out of mixin classes because Mixin 0.7 cannot resolve them there).
- **Mixin source:** the jar bundles no Mixin. MixinBooter reads `MixinConfigs` from the manifest; LiteLoader (no MixinBooter) reads the
  same configs from `litemod.json` and runs Mixin 0.7, so mixins avoid `@ModifyVariable` on constructors and the configs say `minVersion 0.7.5`.
- `convert/`: schematics of Minecraft 1.13+ (`.litematic`, Sponge `.schem`, structure `.nbt`) turned into 1.12 data
  as they are read (`ModernFiles`, `BlockStates`, `ModernNbt`, `ItemIdMaps`), blocks 1.12 lacks (`MissingBlocks`,
  `MissingBlockGuesses`, the window in `convert/gui/`), converting the file on disk (`FileConverter`).
- `importer/`: the one-time import of keybinds and settings from LiteLoader Litematica, Schematica Upgraded and
  Schematica (`OldSettingsImport` reads, `ImportRules` decides, `ImportCore` applies to `LiveTarget`).
- `litematica/`, `malilib/`: masa's code (above).
- `materials/`: the material list's Replace (`MaterialReplace`: swaps the blocks of a row's item in the placement's
  loaded schematic, in the palette or block by block, rebuilds the schematic world, recounts the list; `ReplaceRules`:
  which properties carry over, unit tested). The button comes from `MixinMaterialListEntryWidgetReplace`, the
  placement from `IMixinMaterialListPlacement`.
- `mixin/`: all our patches of Litematica, malilib and Minecraft (`mixins.litematica_upgraded.json`); `IMixin*` are
  accessors. `MixinPerf*` = speed-ups, `Mixin*Modern` = newer-version schematics, `MixinTask*` = server-side
  schematics, `MixinPlayerControllerMPFakeRotation` / `MixinEasyPlaceUtilsUseAction` = Easy Place.
- `perf/`: the speed-ups and their settings (`PerfConfigs`): build threads (`PerfDispatcher`), empty slices
  (`SectionSlices`), ghost move and complete copy (`GhostMove`, `CompleteCopy`), batch drawing, far detail, sparse
  storage (`SparseBits`, `SparseIntArray`, `MemoryBudget`), F3 numbers (`PerfStats`), `PerfGuard` (turns a failing
  speed-up off). Pure rules in `PerfMath` (unit tested).
- `printer/`: the printer (`Printer`) and its helpers (order, look, range, mining, tools, fluids, scaffold...),
  `ContainerFiller` = fill built containers (hoppers ... shulker boxes, double chests joined west/north first),
  `PrinterConfigs` = the "Litematica Upgraded" config tab (printer, speed settings), `PrinterInit` hooks it into malilib.
- `remote/`: `RemoteTasks`, the client side of server-side save / paste / fill / replace / delete.
- `server/`: the server part (accurate placement, air placement, extended reach, shulker restock, `ServerJobs` =
  saves and edits, `ServerConfig`, `PrinterCommand`), what the client knows of it (`ServerFeatures`, `RemoteClient`);
  `server/net/`: the network channel (`Net`) and its messages.

**Tests:**
- `src/test/`: fast unit tests (no game), run by `bash tools/check.sh` with the build; include `CodeHygieneTest`,
  `LangKeysTest`, `RelocationTest`, `SelfTestHygieneTest` (no fixed-position clicks).
- `src/selftest/`: in-world tests, built into a separate selftest jar (never released). `tools/ingame.ps1` runs them on
  the test box in phases: **plain** (server without the mod), **forge** (Forge dedicated server with the mod),
  **optifine** (plain server, OptiFine in the client), **liteloader** (plain server, Forge + LiteLoader and no MixinBooter in the client, `compat/liteloader/`). `bash tools/check-ingame.sh` starts it, `tools/ingame_verdict.py`
  reads the result.
- `test-plan.json`: one line per feature = test id prefix + how many in-game tests must pass.

## Main flows

- **Schematic load -> render:** Litematica's browser loads a file (`litematica/schematic/`); `MixinDataFileUtilsModern`
  turns a newer-version file into 1.12 data first (`convert/`), and `MixinSchematicBrowserScreenModern` /
  `MixinSchematicEntryWidgetModern` open the missing-blocks window. A placement copies the blocks into the schematic
  world (`litematica/world/`, sped up by `MixinPerfPlacingUtils`); `litematica/render/schematic/` builds and draws the
  chunks, with our build threads, slices, batch drawing and move copies patched in by the `MixinPerf*` mixins.
- **Printer tick:** `Printer` is on the Forge event bus (client tick): finds the wanted blocks of the loaded
  placements in reach (`PrinterArea`, `PrinterIteration`, `PrinterOrder`), picks the item, reports the rotation
  (`PrinterLook`, `FakeRotation`) and clicks; with the server part, `ServerAssist` sends accurate / air / ranged messages.
- **Server part:** `CommonProxy` opens the channel (`server/net/Net`); `ServerEvents` tells each client what the server
  supports (`MessageCapabilities` -> `ServerFeatures`). Accurate placement etc. are answered in `server/`. Save /
  paste / edit: the `MixinTask*` mixins hand Litematica's task to `RemoteTasks`, the data goes in slices
  (`server/Slices`) to `ServerJobs`, which runs it on server ticks and reports back (`MessageEditStatus`).
- **Material Replace:** a placement's material list row -> Replace (`MixinMaterialListEntryWidgetReplace`) ->
  `BlockPicker` -> `MaterialReplace.replace` changes the loaded schematic's palettes, marks it modified and the
  placements of that schematic for rebuild; once the rebuild queue is empty the list counts again (client tick).
- **One-time import:** `ClientProxy.preInit` calls `OldSettingsImport.readOldFiles` once (never again); the result is
  applied and reported by one chat line on the first world join.

## How to port to another Minecraft version

Only 1.12.2 (Forge, MCP `stable_39`) exists. A second version starts with the shared-core + thin-version-layer layout
(project rule 1): the version-free logic moves to a shared module, per version only the hooks. Version-specific today:
- masa's code for that version (`../reference/` branch) through `tools/convert_upstream.py`, plus new
  `tools/forge-overrides/` for its loader glue.
- `mixin/` (targets and method names), `proxy/` and `LitematicaUpgraded.java` (loader), `server/net/` (Forge
  networking), `compat/` (OptiFine), `build.gradle`.
- The game-touching parts of `printer/`, `server/` and `perf/` (clicks, block states, renderers). Mostly version-free
  already: `perf/PerfMath`, `SparseBits` / `SparseIntArray`, `importer/ImportRules`, `convert/MissingBlockGuesses`, `materials/ReplaceRules`,
  `server/Slices`.
