# Litematica Upgraded changelog

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
