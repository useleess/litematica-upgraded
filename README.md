# Litematica Upgraded

Litematica for **Forge 1.12.2**, with a printer, server-side support and more. Needs
[MixinBooter](https://github.com/CleanroomMC/MixinBooter) 11.18 or newer (tested with 11.18 and 12.2).

The same jar also runs on a Forge 1.12.2 dedicated server (with MixinBooter): it then gives the printer accurate placement, server-side saving, paste, fill, replace, delete and move (full NBT),
air placement, extended reach and shulker restock, and operators can switch the printer off for everybody with
`/litematicaupgraded printer on|off|status`. Options: `config/litematica_upgraded-server.cfg`.

Schematics saved on Minecraft 1.13 and newer (`.litematic`, Sponge `.schem`, vanilla structure `.nbt`) open in Litematica's own browser: they are converted to 1.12 blocks, states, items and sign text as they are read. Blocks 1.12 does not have are listed in a window where you can pick a stand-in block for each; "Use these" converts the file on disk.

## Credits and licences

- **Litematica** and **malilib** by masa (maruohon) - https://github.com/maruohon/litematica,
  https://github.com/maruohon/malilib - LGPL-3.0. This mod is built from their 1.12.2 code (`ornithe/1.12.2`
  branches), moved to Forge; the changes are in this repository's history. Licence: [LICENSE.txt](LICENSE.txt).
- **Printer**, the newer-version schematic converter and other additions come from our Schematica Upgraded, built on
  **Schematica** by Lunatrius and **Schematica Plus** by HackerRouter (MIT).
- **Speed-ups** (threaded chunk builds, skipped empty slices, fast placement copy, ghost move, F3 numbers) come from our
  **Litematica Turbo** (Useleess); the sparse storage of huge, mostly empty schematics and the memory budget come from our
  Schematica Upgraded (**Schematica** by Lunatrius, **Schematica Plus** by HackerRouter, MIT). Every one has a setting in the
  "Litematica Upgraded" tab.
- **MixinBooter** by CleanroomMC - a separate download, not bundled.
- Everything else: Useleess, LGPL-3.0.

## For developers

- [CODE_MAP.md](CODE_MAP.md): what lives where, the main flows and how to port to another Minecraft version.
- `python -I tools/convert_upstream.py` copies masa's code from `../reference/` (re-run after updating the
  reference clones); our Forge versions of loader files live in `tools/forge-overrides/`.
- `bash tools/check.sh`: every upstream file present + build + unit tests (test box).
  `bash tools/check-smoke.sh`: also starts a real Forge game with MixinBooter on the test box.
- Only Minecraft 1.12.2 for now. A second version gets the shared-core + thin-version-layer layout first.
