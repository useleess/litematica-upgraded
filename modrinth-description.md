# Litematica Upgraded

**Litematica for Forge 1.12.2** - masa's schematic mod, moved to Forge, with a printer, server support and a lot more
speed. Needs [MixinBooter](https://modrinth.com/mod/mixinbooter) (11.18 or newer).

## What you get

Everything Litematica already does (schematic placements, ghost blocks, verifier, material list, area selection and
saving, schematic projects), plus:

- **Printer** - builds the placed schematic around you in survival, from your inventory: right rotation and facing,
  sensible build order, mines blocks that are wrong or in the way, fills containers, places fluids, snow layers and
  sign text, and pauses while you mine by hand. Range, speed and every part can be switched in the settings.
- **Schematics from newer versions** - `.litematic`, Sponge `.schem` and vanilla `.nbt` files saved on 1.13 and newer
  open straight away, converted to 1.12 blocks. Blocks 1.12 doesn't have can be swapped for a stand-in of your choice.
- **Container contents** are kept when saving and pasting.
- **Fast** - chunk building on several threads, huge mostly-empty schematics that use little memory, and the whole
  schematic stays visible while you move a placement. Works with OptiFine and with VBOs off.
- **Keeps your setup** - on first start it copies your keybinds and settings from LiteLoader Litematica, Schematica
  Upgraded or old Schematica, so you don't have to set them up again.

## On a server (optional)

The same jar also runs on a Forge 1.12.2 server (with MixinBooter). Then the printer gets accurate placement, air
placement, extended reach and shulker restock, and you can save, paste, fill, replace, delete and move areas on the
server with full block data. Operators can switch the printer off for everyone:
`/litematicaupgraded printer on|off|status`. Without the server part everything still works client-side.

## Credits

- **Litematica** and **malilib** by masa (LGPL-3.0) - this mod is built from their 1.12.2 code.
- Printer and converter from **Schematica Upgraded**, built on **Schematica** by Lunatrius and **Schematica Plus** by
  HackerRouter (MIT).
- Speed-ups from **Litematica Turbo**.
- **MixinBooter** by CleanroomMC (separate download).

Bugs and ideas: [our Discord](https://discord.gg/fkNcMPTPuy).
