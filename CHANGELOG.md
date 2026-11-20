# Litematica Upgraded changelog

## 0.3.0 (beta) - 2026-11-20

### Added
- Schematic Edit mode keeps block states when replacing (#14): swapping a block for another (replace in a direction or
  replace all identical) keeps how it was turned and set - facing, open / closed, top / bottom half, door hinge, log
  axis, sign rotation, repeater delay - so swapping materials no longer resets every door, gate and stair. The material
  itself (wood type, colour) is still the one you hold. A door or tall plant swaps both halves at once.
- Shulker Restock Amount setting (#18, Printer tab): when the printer or Easy Place takes a missing block out of a
  shulker box in your inventory, it takes a full stack (as before), half a stack, or a single item at a time, so your
  inventory doesn't fill up with part stacks.

### Fixed
- Out of memory with big schematics (#19): every 16-block piece of a shown schematic kept a full copy of its see-through
  blocks and colour overlay for re-sorting, though only the nearest pieces are ever re-sorted. A big, packed schematic
  filled gigabytes that way (1.4 GB at view distance 6 in our test, far more at higher view distances). Now only pieces
  within 48 blocks keep one; a 528 x 256 x 528 schematic a third full now stays around 0.3 GB while flying across it.
- Cauldrons (#16): with the server part (LU on the server, or Useleess Carpet), Easy Place and the printer put cauldrons
  down empty again instead of full of water. The server now never fills in a block's contents or amount from the
  schematic (water, snow layers, cake bites, sapling growth), only what a player chooses by placing.
- Doors (#13): the printer no longer breaks a door's upper half with "break wrong blocks" / "break wrong state" on, and
  places doors facing the schematic's way. Easy Place aimed at either half builds the whole door from its lower half,
  facing the right way, without a ghost item. Tall plants (sunflowers, tall grass) are built from their lower half too.
- Smooth moving with several schematics shown (#15): moving one keeps the whole schematic visible and smooth, and the
  others stay in place, as long as they don't share a 16-block section with the moved one (schematics that overlap
  still move the normal way).
- A schematic reaching above the world's top no longer leaves invisible leftovers behind after it is moved or removed
  (they could stop the smooth move of another schematic).
- Broken button pictures (#12) with masa's LiteLoader malilib or Litematica installed: their pictures share our
  old file paths and replaced ours. Our pictures now live in our own folder (`assets/litematica_upgraded/textures/gui/`;
  resource packs that changed them need the new path).

## 0.2.5 (beta) - 2026-11-04

- The printer places blocks in mid-air with nothing around them, on any server, the way Easy Place does, in their exact state (for example an upside-down stair with only the floor nearby). Sand and gravel still wait for something under them, and the Easy Place Click Adjacent setting turns this off for servers that refuse clicks on empty space.
- On Carpet servers with `relaxedBlockPlacement` on, the printer places fence gates and pumpkins in mid-air too, with or without Carpet Client. It finds out by trying once; if the server refuses, fence gates go back to using a temporary block for that world.
- The printer now works with Easy Place switched on. Before, while you looked at a block of the schematic, its clicks turned into Easy Place clicks at your crosshair, so only the block you looked at got built.

## 0.2.4 (beta) - 2026-11-04

- The LGPL-3.0 licence text now ships inside the jar (`META-INF/LICENSE-LGPL-3.0.txt`), next to the MIT one.
- Easy Place now takes a block you don't carry out of a shulker box in your inventory, like the printer (#7). Works in single player, on servers with Litematica Upgraded, and on Carpet servers with Useleess Carpet's rule on. The first click fetches the block, the next click (or holding the button) places it.
- Easy Place and the printer place blocks in the schematic's exact state (#9). In single player and on servers with Litematica Upgraded, the server sets the whole state in one click: facing, top/bottom half, axis, repeater delay, comparator mode, open trapdoors / fence gates / doors and switched-on levers, even where no click could (a top-half stair or trapdoor with only the ground to click on). Redstone parts are placed unpowered and the world powers them; iron doors and trapdoors stay closed (only redstone opens them); a block's kind (colour, variant) and growth are never changed. On other servers Easy Place now does what the printer does: it clicks the placed block until trapdoors, fence gates and levers are open / on as in the schematic and repeaters / comparators are set, and aiming Easy Place at an already built block in the wrong state fixes it.
- Easy Place places slabs in the top layer of the world (y 255) again: bottom slabs and the second half of double slabs failed there with "cannot be placed with Easy Place", because the server refuses a click on a top face that high (#10).
- Accurate placement now also covers doors (open and hinge), signs and banners, which used to ignore the schematic's state on servers with the LU server part or Useleess Carpet.
- New: Grab From Containers (#11), a switch you can put on a key, like Easy Place. While it's on, opening a chest, shulker box, hopper, dropper or dispenser takes what the selected layers of your schematic still need, the most needed first, a whole stack at a time. What you carry already counts. It only uses normal clicks, so it works on any server; Grab Click Delay slows it down for picky servers.

## 0.2.3 (beta) - 2026-11-01

- Clearer Easy Place setting names: the on/off switch is now just **Easy Place** (was "Easy Place Mode"), and the extra key is **Easy Place Hold Key (optional)** (was "Easy Place Activation"), with tooltips that say what each one does. Your saved settings are kept.
- Fixed: entities in a schematic (iron golems, minecarts, armor stands...) were copied once for every chunk the schematic touches, so a farm with 30 golems showed (and pasted) hundreds of them and the game slowed to a crawl. Each entity is now placed once. Also fixed old preview entities staying in memory after the preview was refreshed.
- New: loading a schematic that was saved after a paste with this old bug (from masa's original 1.12 Litematica) shows a chat warning with how many mobs were copied and how to clean it up. Nothing in the file is changed.
- Fixed a crash (ConcurrentModificationException in the message display) when a message was shown while the 3D preview read a schematic in the background (#5). Messages from background work now always go through the game thread.

## 0.2.2 (beta) - 2026-11-01

- Fixed a crash right after joining a server when masa's LiteLoader Litematica was also installed (#2): our mixin files had the same names as his, so ours were not loaded. They now have their own names, and the schematic renderer no longer crashes if it is asked to draw before it is ready. masa's LiteLoader malilib, MiniHUD, Tweakeroo and Item Scroller now also start in a Forge + LiteLoader game next to Litematica Upgraded: his malilib has one hook that only fits plain LiteLoader and stopped the game on Forge (with or without our mod); Litematica Upgraded moves it to Forge's spot while the game starts. Litematica Upgraded replaces Litematica only - keep your other masa mods. If masa's Litematica is still installed too, the game no longer crashes: Litematica Upgraded tells you once in chat to remove it (some schematic features may not work while both are installed).
- Fixed the game freezing after joining a server with masa's LiteLoader malilib + Tweakeroo installed (#4): same cause as #2; a new test game checks that exact setup (Forge first, LiteLoader, OptiFine, malilib, MiniHUD, Tweakeroo, Item Scroller) keeps running after joining.
- Fixed the game not starting with LiteLoader + OptiFine (#2): the OptiFine safety switch did not work in LiteLoader's older Mixin, so an OptiFine-moved hook stopped the game. It now works in every load order (also when Prism starts LiteLoader before Forge), and masa's malilib hook is safe with OptiFine too.
- New: a rotating 3D preview of the selected schematic in the schematic browser (lower part of the info panel; setting "3D Preview" in the Litematica Upgraded tab, on by default). It shows the build with its real blocks, turns on its own, drag with the left mouse to turn and tilt it, scroll to zoom. It appears almost instantly and is built in the background, so the menu never freezes; very big schematics show a smaller copy first (labelled 1:2, 1:3...), then every block. It switches itself off if anything goes wrong.
- New: the "Config" button in Forge's Mods list (main menu -> Mods -> Litematica Upgraded) is no longer greyed out: it opens the Litematica Upgraded settings, and Done / Esc take you back to the list. If the settings screen cannot open, you stay on the list and a message says so.

## 0.2.1 - 2026-10-31

- Credits completed: the mod list and the Credits setting now also name Schematica / Schematica Plus (MIT) and the translators.
- Fixed a crash about a second after joining a world (NullPointerException in RenderGlobalSchematic, #1).

## 0.2.0 - 2026-10-30

- Works next to LiteLoader: don't install MixinBooter there (the mod uses LiteLoader's Mixin). Without LiteLoader, MixinBooter is still needed; with neither, the game now says so clearly instead of crashing.
- The MIT licence of the code that came from Schematica Upgraded (printer, converter, sparse storage) now ships in the jar (META-INF/LICENSE-MIT-Schematica.txt).
- Material list: every row of a placement's material list has a Replace button - pick another block and every block of that kind in the placement becomes it (stairs keep their facing and half; ghost blocks, verifier, printer and counts follow at once). The schematic file only changes when you save it; replace back to undo.
- The printer's Fill Containers now also fills chests, trapped chests (double chests too), furnaces (input and fuel), brewing stands and shulker boxes.
- In 13 languages: Chinese (simplified and traditional), Russian, Ukrainian, Spanish, French, Italian, Portuguese (Brazil), Japanese, Korean, Swedish, Turkish and German.

## 0.1.0 (beta) - 2026-10-30

First public beta: masa's **Litematica 0.40.1 + malilib 0.60.2 for Forge 1.12.2** (needs MixinBooter 11.18 or newer;
malilib is built in).

- **Printer**: builds the placed schematic around you in survival from your inventory - right rotation and facing (also
  for Easy Place, on any server), redstone-aware order, picks the item itself, mines wrong or extra blocks (off by
  default), auto tool, fills containers, fluids, snow layers, sign text, scaffold for blocks over air, destroy assist,
  layer range limit, pauses while you mine by hand. Every part has a setting in the new "Litematica Upgraded" tab;
  toggle hotkey M,P.
- **Server part (optional)**: the same jar on a Forge 1.12.2 server gives accurate placement, air placement, extended
  reach, shulker restock and server-side save / paste / fill / replace / delete / move with full block data.
  Operators: `/litematicaupgraded printer on|off|status`. Everything still works client-side without it.
- **Schematics from 1.13 and newer** (`.litematic`, Sponge `.schem`, vanilla `.nbt`) open directly, converted to 1.12;
  blocks 1.12 lacks can be swapped for a stand-in, saved back to the file.
- **Container contents** are kept when saving and pasting.
- **Speed**: chunk building on several threads, skipped empty slices, huge mostly-empty schematics in little memory,
  the whole schematic stays visible while you move a placement (with a graphics-memory budget), works with OptiFine
  and with VBOs off.
- **Keeps your setup**: on first start your keybinds and settings are copied once from LiteLoader Litematica,
  Schematica Upgraded or old Schematica; nothing you already changed is overwritten.
- Fixes over the original 1.12.2 code: translations load on Forge, pick block / Easy Place find items on a fresh
  install, the version shows in the screens, the Loaded Schematics screen no longer crashes with MixinBooter 12.2.

(nothing released yet)
