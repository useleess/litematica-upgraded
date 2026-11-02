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
- `src/main/resources/mixins.litematica_upgraded.litematica.json`, `mixins.litematica_upgraded.malilib.json`: masa's own mixins.

**Our code**, `src/main/java/com/useleess/litematicaupgraded/`:
- `gui/`: shared screens of our own (`BlockPicker`: pick a block by name, used by the missing-blocks window and Replace;
  `ModListConfigFactory`: the `guiFactory` of the `@Mod`, makes Forge's Mods list "Config" button open `ConfigScreen` with the
  list as parent, stays on the list with a toast if the screen cannot be built).
- `LitematicaUpgraded.java`: the Forge `@Mod` (no hard dependency; `guiFactory` = `gui/ModListConfigFactory`; `MixinCheck.require()` stops with a clear message when no Mixin is installed); hands everything to the proxy.
- `proxy/`: `ClientProxy` (Litematica, malilib, printer, client side of the server part, the one-time import),
  `CommonProxy` (server options, network channel, server part), `ServerProxy` (dedicated server: common part only).
- `compat/`: `CompatMixinPlugin`, makes hooks optional that OptiFine (malilib's render hooks) or masa's own Litematica (ours) take; `MasaLitematicaNotice` tells the player to remove masa's Litematica (also answers Mixin 0.7's
  `preApply`/`postApply` signature, using the compile-only stub in `src/mixin07/`); `MixinCheck` / `ClientError` (no Mixin installed: readable
  error); `InterfaceAccess` (casts to our accessor interfaces, kept out of mixin classes because Mixin 0.7 cannot resolve them there).
- `earlyfix/`: `MasaMalilibForgeFix` (LiteLoader games, via litemod.json `tweakClass` = `MasaMalilibForgeFixTweaker`): moves masa's
  own LiteLoader malilib screen hook to Forge's call, so his malilib, MiniHUD, Tweakeroo, Item Scroller start on Forge next to ours.
  Nothing else may live in this package: the launcher loads a tweaker's whole package outside the game.
- **Mixin source:** the jar bundles no Mixin. MixinBooter reads `MixinConfigs` from the manifest; LiteLoader (no MixinBooter) reads the
  same configs from `litemod.json` and runs Mixin 0.7, so mixins avoid `@ModifyVariable` on constructors and the configs say `minVersion 0.7.5`.
- `convert/`: schematics of Minecraft 1.13+ (`.litematic`, Sponge `.schem`, structure `.nbt`) turned into 1.12 data
  as they are read (`ModernFiles`, `BlockStates`, `ModernNbt`, `ItemIdMaps`), blocks 1.12 lacks (`MissingBlocks`,
  `MissingBlockGuesses`, the window in `convert/gui/`), converting the file on disk (`FileConverter`).
- `importer/`: the one-time import of keybinds and settings from LiteLoader Litematica, Schematica Upgraded and
  Schematica (`OldSettingsImport` reads, `ImportRules` decides, `ImportCore` applies to `LiveTarget`).
- `litematica/`, `malilib/`: masa's code (above).
- `entities/`: `CopiedEntities` warns in chat when a loaded schematic was saved after a paste with the entity copy bug of
  masa's 1.12 Litematica (mobs saved as perfect twins, all the same number of times; armor stands / minecarts are no
  evidence), unit tested; hooked in by `MixinSchematicHolderCopiedEntities`.
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
- `preview/`: the 3D view in the schematic browser's info panel (turns on its own, drag with the left mouse to turn / tilt, wheel to zoom 0.25x-4x). `PreviewBuilder` (reads the file on the one "Litematica Upgraded preview" thread, shrinks a big file into a smaller schematic with `DownsampledGrid` - one real block per n x n x n cell, n from `PreviewMath.stepFor` so the longest side is at most 256, an opaque block wins its cell, shrunk further while over 200,000 blocks; then `PreviewSections` splits it into 16x16x16-cell sections and `SectionRunner` builds them side by side on a build pool sized like Turbo's (`PerfMath.workers`), each section drawn with vanilla's block renderer, always textured, in two passes: opaque + cut-out, then translucent; ambient occlusion off while building, full-bright light from `PreviewBlockAccess`, faces between neighbours culled. Over 20,000 blocks a rougher copy (`PreviewMath.ROUGH_BLOCKS`) is built first, then the shrunk one; a shrunk file with at most `PreviewMath.FULL_MAX_BLOCKS` blocks then gets its full 1:1 model built last, the same way, from every block of the file, handed over once complete; its build stops and the shrunk model stays when its vertex data passes `FULL_MAX_VERTICES` (`fullModelAllowed`)). `PreviewModel` (the sections as they finish, copied out as plain arrays - each build thread reuses its own builders, so no direct buffer is made per section; the render thread turns them into display lists, at most `UPLOAD_BUDGET_MS` per frame, so the view fills in piece by piece). `PreviewView` (render thread: draws what is uploaded, swaps the rough copy for the shrunk one when it is all there, uploads the full 1:1 model after it and draws it instead at every zoom (only once all of it is uploaded; `modelToDraw`; with it the zoom limit rises to `maxZoom`), keeps both in the `LruCache` (the full one under the key + `|1:1`), keeps the last 4 full models in an `LruCache` so going back to a file draws in the first frame, cancels the build of a file that is no longer selected, draws clipped with `ScissorMath`, yaw / pitch / zoom from the mouse via `PreviewMath`, fitted to the view rectangle by `PreviewMath.scaleToFit`; shows a percent while building and the `1:N` label of the model actually drawn (none for the full one); frees everything when the browser is no longer drawn; switches itself off on any error). `PreviewTiming` (read / shrink / build / upload / first picture / complete times of each preview, logged once as a `PERF preview` line, and the full model's build / upload / size as a `PERF preview-detail` line and read by the tests). `PreviewLayout` (the rectangle below the detail lines), `PreviewConfigs` (the "3D Preview" setting). Hooked in by `MixinSchematicInfoWidgetPreview` (draws, forwards click / release / scroll, also moves/shrinks masa's saved picture so nothing overlaps). Pure rules are unit tested.
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
  **optifine** (plain server, OptiFine in the client), **liteloader** (plain server, Forge + LiteLoader and no MixinBooter in the client, masa's five LiteLoader mods, `compat/liteloader/`), **liteloader-first** (LiteLoader started before Forge, OptiFine in mods/1.12.2, masa's mods but not his Litematica: a player's setup, `compat/liteloader-first/`), **liteloader-optifine** (the Discord #4 setup: Forge first, LiteLoader after it, OptiFine in mods/1.12.2, masa's mods but not his Litematica; the client must keep ticking and drawing after joining, `compat/liteloader-optifine/`), **liteloader-alone** (Forge + LiteLoader, no other mods, `compat/liteloader-alone/`). `bash tools/check-ingame.sh` starts it, `tools/ingame_verdict.py`
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
