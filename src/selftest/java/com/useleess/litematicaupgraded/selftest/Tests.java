package com.useleess.litematicaupgraded.selftest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URL;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraft.world.World;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;

import com.useleess.litematicaupgraded.LitematicaUpgraded;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.gui.LoadedSchematicsListScreen;
import com.useleess.litematicaupgraded.litematica.data.SchematicHolder;
import com.useleess.litematicaupgraded.litematica.gui.ConfigScreen;
import com.useleess.litematicaupgraded.litematica.gui.MainMenuScreen;
import com.useleess.litematicaupgraded.litematica.gui.SchematicBrowserScreen;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.Schematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacementManager;
import com.useleess.litematicaupgraded.litematica.schematic.util.SchematicFileUtils;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelection;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelectionManager;
import com.useleess.litematicaupgraded.litematica.selection.CornerDefinedBox;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.litematica.world.WorldSchematic;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.printer.PrinterInit;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;
import com.useleess.litematicaupgraded.malilib.mixin.access.NBTTagLongArrayMixin;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;

/**
 * The in-world tests. Each test checks real effects (block states, sizes, file contents read back), finds screens by
 * their class, and never clicks a fixed position. Ids: the prefix is the feature in test-plan.json.
 */
final class Tests {
    /** Placements the render tests put into the world (the second render test reuses the first one's). */
    private static SchematicPlacement renderPlacement;
    private static BlockPos renderPos;
    private static String renderBlock;
    private static int renderTicks;
    private static int screenTicks;

    /** What the render world looks like now, for the failure message of a render test that waits too long. */
    private static String renderStatus() {
        World world = SchematicWorldHandler.getSchematicWorld();
        SchematicPlacementManager m = DataManager.getSchematicPlacementManager();
        String seen = world == null || renderPos == null ? "-" : BlockState.of(world.getBlockState(renderPos.toVanillaPos())).getRegistryNameStr();
        return "world=" + (world != null) + " pending=" + m.hasPendingRebuilds() + " subChunks=" + m.getAllTouchedSubChunks().size()
                + " block at " + renderPos + "=" + seen + " wanted=" + renderBlock;
    }

    private Tests() {}

    static void register(Runner.Registry r) {
        // import/: first, before any other test changes keys or settings (they put the sample values back afterwards)
        ImportTests.register(r);
        // port/: the Forge port, its jar and its mixins
        r.add("port/no-fabric-or-ornithe-code", c -> {
            File jar = modJar();
            int classes = 0;
            try (JarFile jf = new JarFile(jar)) {
                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    if (!e.getName().startsWith("com/useleess/litematicaupgraded/") || !e.getName().endsWith(".class")) continue;
                    classes++;
                    String text = new String(readAll(jf.getInputStream(e)), StandardCharsets.ISO_8859_1);
                    c.check(!text.contains("net/fabricmc") && !text.contains("net/ornithemc"), e.getName() + " uses Fabric/Ornithe code");
                }
            }
            c.check(classes > 100, "only " + classes + " mod classes in " + jar.getName());
        });
        r.add("port/mixins-applied", c -> {
            String configs;
            try (JarFile jf = new JarFile(modJar())) {
                configs = jf.getManifest().getMainAttributes().getValue("MixinConfigs");
                // masa's LiteLoader Litematica / malilib use these names: one of ours would hide his or the other way (Discord #2)
                for (String his : new String[] {"mixins.litematica.json", "mixins.malilib.json", "mixins.litematica.refmap.json", "mixins.malilib.refmap.json"}) {
                    c.check(jf.getEntry(his) == null, his + " is in our jar - the same name as in masa's mods");
                }
            }
            for (String name : new String[] {"mixins.litematica_upgraded.litematica.json", "mixins.litematica_upgraded.malilib.json"}) {
                c.check(configs != null && configs.contains(name), name + " is not in the jar's MixinConfigs");
                // every mixin class the json lists is in the jar; "applied to its target" is not checked yet (Class.forName
                // from the game gives ClassNotFound for mixin.debug.GuiOverlayDebugMixin: open, see LADDER_NOTES.md)
                for (String mixin : mixinClasses(name)) {
                    String entry = mixin.replace('.', '/') + ".class";
                    try (JarFile jf = new JarFile(modJar())) {
                        c.check(jf.getEntry(entry) != null, entry + " is not in the jar");
                    }
                }
                // MixinMinecraft's tick hook runs (it stamps the tick start); without it the render queue never runs
                c.check(DataManager.getClientTickStartTime() > 0, "the client tick hook never ran (MixinMinecraft not applied?)");
            }
        });

        // every mixin's target class loads with the mixin applied: mixins apply only when their target first loads, so a broken
        // one (2026-10-29: a @Shadow of a parent's field) only crashed when that screen was opened - this loads them all now
        r.add("port/every-mixin-target-loads-with-its-mixin", c -> {
            int targets = 0;
            for (String config : new String[] {"mixins.litematica_upgraded.litematica.json", "mixins.litematica_upgraded.malilib.json", "mixins.litematica_upgraded.json"}) {
                for (String mixin : mixinClasses(config)) {
                    for (String target : mixinTargets(mixin)) {
                        targets++;
                        try {
                            Class.forName(target, false, Tests.class.getClassLoader());
                        } catch (Throwable e) {
                            Throwable root = e;
                            while (root.getCause() != null) root = root.getCause();
                            c.check(false, target + " (" + mixin + ") does not load: " + root);
                        }
                    }
                }
            }
            c.check(targets > 50, "only " + targets + " mixin targets found");
        });

        // ui/: Litematica and malilib screens, found by class
        // the screenshot waits a few ticks: right after opening, the screen has not drawn yet
        r.waitFor("ui/main-menu-opens", 200, c -> {
            screenTicks = 0;
            BaseScreen.openScreen(new MainMenuScreen());
        }, c -> {
            if (!(c.mc.currentScreen instanceof MainMenuScreen) || ++screenTicks < 10) return false;
            Runner.shot(c.mc, "ui-main-menu");
            return true;
        });
        r.waitFor("ui/schematic-browser-opens", 200, c -> {
            screenTicks = 0;
            BaseScreen.openScreen(new SchematicBrowserScreen());
        }, c -> {
            if (!(c.mc.currentScreen instanceof SchematicBrowserScreen) || ++screenTicks < 10) return false;
            Runner.shot(c.mc, "ui-schematic-browser");
            return true;
        });
        r.waitFor("ui/loaded-schematics-list-opens-with-a-schematic", 200, c -> {
            screenTicks = 0;
            SchematicHolder.INSTANCE.clearLoadedSchematics();
            SchematicHolder.INSTANCE.addSchematic(load(c.mc, "cobblestone_farm.litematic"), false);
            BaseScreen.openScreen(new LoadedSchematicsListScreen());
        }, c -> {
            if (!(c.mc.currentScreen instanceof LoadedSchematicsListScreen) || ++screenTicks < 10) return false;
            Runner.shot(c.mc, "ui-loaded-schematics");
            c.mc.displayGuiScreen(null);
            SchematicHolder.INSTANCE.clearLoadedSchematics();
            return true;
        });
        r.waitFor("ui/config-screen-opens", 200, c -> ConfigScreen.openConfigScreen(), c -> c.mc.currentScreen != null
                && c.mc.currentScreen.getClass().getName().contains("Config"));
        ForgeButtonTests.register(r);

        // schematic/: .litematic files load, and what they hold matches the file
        r.add("schematic/load-cobblestone-farm", c -> {
            Schematic s = load(c.mc, "cobblestone_farm.litematic").schematic;
            checkMatchesFile(c, s, nbt(c.mc, "cobblestone_farm.litematic"));
        });
        r.add("schematic/load-worldeater", c -> {
            Schematic s = load(c.mc, "worldeater_1_12.litematic").schematic;
            checkMatchesFile(c, s, nbt(c.mc, "worldeater_1_12.litematic"));
        });
        r.add("schematic/block-count-worldeater", c -> {
            Schematic s = load(c.mc, "worldeater_1_12.litematic").schematic;
            NBTTagCompound meta = nbt(c.mc, "worldeater_1_12.litematic").getCompoundTag("Metadata");
            c.check(solidCount(s) == longTag(meta, "TotalBlocks"), "solid blocks " + solidCount(s) + " != file " + longTag(meta, "TotalBlocks")
                    + " (sample: " + sample(s) + ")");
            c.check(solidCount(s) == s.getMetadata().getTotalBlocks(), "metadata total differs from the blocks");
        });
        r.add("schematic/block-count-minewave", c -> {
            Schematic s = load(c.mc, "minewave_worldeater.litematic").schematic;
            NBTTagCompound meta = nbt(c.mc, "minewave_worldeater.litematic").getCompoundTag("Metadata");
            // counted from the file's own palette and packed states (the metadata's TotalBlocks is one more: not used)
            c.check(solidCount(s) == fileSolidCount(nbt(c.mc, "minewave_worldeater.litematic")),
                    "solid blocks " + solidCount(s) + " != the file's " + fileSolidCount(nbt(c.mc, "minewave_worldeater.litematic"))
                    + " (TotalBlocks " + longTag(meta, "TotalBlocks") + ")");
        });
        r.add("schematic/placement-box-matches-size", c -> {
            LoadedSchematic loaded = load(c.mc, "cobblestone_farm.litematic");
            SchematicPlacement p = SchematicPlacement.create(loaded, new BlockPos(10, 64, 20), "selftest-box", true);
            IntBoundingBox box = p.getEnclosingBox();
            Vec3i size = loaded.schematic.getEnclosingSize();
            c.check(box.getMaxX() - box.getMinX() + 1 == size.getX(), "box width " + (box.getMaxX() - box.getMinX() + 1) + " != " + size.getX());
            c.check(box.getMaxY() - box.getMinY() + 1 == size.getY(), "box height differs from the schematic");
            c.check(box.getMaxZ() - box.getMinZ() + 1 == size.getZ(), "box depth differs from the schematic");
        });
        r.add("schematic/placement-registers-in-manager", c -> {
            LoadedSchematic loaded = load(c.mc, "minewave_worldeater.litematic");
            SchematicPlacement p = SchematicPlacement.create(loaded, new BlockPos(-40, 64, 30), "selftest-register", true);
            SchematicPlacementManager m = DataManager.getSchematicPlacementManager();
            int before = m.getAllSchematicPlacements().size();
            m.addSchematicPlacement(p, false);
            c.check(m.getAllSchematicPlacements().size() == before + 1, "the placement was not added");
            c.check(m.getAllSchematicPlacements().contains(p), "the manager does not hold the placement");
            m.removeSchematicPlacement(p, true);
        });

        // render/: placed schematics reach the schematic world and get their chunks built
        // a schematic renderer asked to draw before its schematic world exists (Discord #1: crash ~1 s after joining)
        r.add("render/no-schematic-world-yet-does-not-crash", c -> {
            com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic early =
                    new com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic(c.mc);
            early.setWorldAndLoadRenderers(null);
            net.minecraft.client.renderer.culling.Frustum camera = new net.minecraft.client.renderer.culling.Frustum();
            camera.setPosition(c.mc.player.posX, c.mc.player.posY, c.mc.player.posZ);
            early.setupTerrain(c.mc.player, 0.5, camera, 1, false);
            early.setupTerrain(c.mc.player, 0.5, camera, 2, false);
        });
        // every step of a whole frame, both render paths, before the schematic world exists (Discord #2: crash in
        // updateChunks right after joining a server, the step after the #1 fix)
        r.add("render/whole-frame-before-schematic-world-does-not-crash", c -> {
            com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic early =
                    new com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic(c.mc);
            early.setWorldAndLoadRenderers(null);
            net.minecraft.client.renderer.culling.Frustum camera = new net.minecraft.client.renderer.culling.Frustum();
            camera.setPosition(c.mc.player.posX, c.mc.player.posY, c.mc.player.posZ);
            for (int frame = 1; frame <= 3; frame++) {
                early.setupTerrain(c.mc.player, 0.5, camera, frame, false);
                early.updateChunks(System.nanoTime() + 1_000_000L);
                for (net.minecraft.util.BlockRenderLayer layer : net.minecraft.util.BlockRenderLayer.values()) {
                    early.renderBlockLayer(layer, 0.5, c.mc.player);
                }
                early.renderEntities(c.mc.player, camera, 0.5f);
                early.renderBlockOverlays();
            }
        });
        r.waitFor("render/schematic-world-has-blocks", 600, c -> {
            LoadedSchematic loaded = load(c.mc, "minewave_worldeater.litematic");
            // next to the player, so the chunks around it are loaded and the queue builds them
            BlockPos origin = new BlockPos((int) c.mc.player.posX + 8, (int) c.mc.player.posY, (int) c.mc.player.posZ);
            renderPlacement = SchematicPlacement.create(loaded, origin, "selftest-render", true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(renderPlacement, false);
            // the solid block nearest to the player: its chunk is one the game has loaded
            double best = Double.MAX_VALUE;
            for (SchematicRegion region : loaded.schematic.getRegions().values()) {
                Vec3i size = absSize(region);
                BlockContainer container = region.getBlockContainer();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockState state = container.getBlockState(x, y, z);
                            if (state.isAirMaterial()) continue;
                            BlockPos pos = renderPlacement.getPosition().add(region.getRelativePosition()).add(x, y, z);
                            double distance = Math.abs(pos.toVanillaPos().getX() - c.mc.player.posX) + Math.abs(pos.toVanillaPos().getZ() - c.mc.player.posZ);
                            if (distance < best) {
                                best = distance;
                                renderPos = pos;
                                renderBlock = state.getRegistryNameStr();
                            }
                        }
                    }
                }
            }
            c.check(renderPos != null, "the schematic has no block to look for");
        }, c -> {
            World world = SchematicWorldHandler.getSchematicWorld(); // the vanilla type: its method is remapped
            if (++renderTicks > 500) throw new AssertionError(renderStatus());
            if (world == null) return false;
            IBlockState state = world.getBlockState(renderPos.toVanillaPos());
            return BlockState.of(state).getRegistryNameStr().equals(renderBlock);
        });
        r.waitFor("render/chunks-built", 600, c -> {
            c.check(renderPlacement != null, "the placement of the first render test is missing");
        }, c -> {
            if (++renderTicks > 500) throw new AssertionError(renderStatus());
            SchematicPlacementManager m = DataManager.getSchematicPlacementManager();
            return !m.hasPendingRebuildForChunk(renderPos.toVanillaPos().getX() >> 4, renderPos.toVanillaPos().getZ() >> 4)
                    && !m.getAllTouchedSubChunks().isEmpty();
        });

        // selection/: corners, boxes, sizes
        r.add("selection/box-size-from-corners", c -> {
            CornerDefinedBox box = new CornerDefinedBox(new BlockPos(5, 70, -3), new BlockPos(1, 64, 4));
            Vec3i size = box.getSize(); // the signed difference of the corners
            c.check(Math.abs(size.getX()) == 5 && Math.abs(size.getY()) == 7 && Math.abs(size.getZ()) == 8, "size " + size + " is not 5 x 7 x 8");
        });
        r.add("selection/box-bounds-from-corners", c -> {
            IntBoundingBox b = new CornerDefinedBox(new BlockPos(5, 70, -3), new BlockPos(1, 64, 4)).asIntBoundingBox();
            c.check(b.getMinX() == 1 && b.getMinY() == 64 && b.getMinZ() == -3, "min corner is wrong");
            c.check(b.getMaxX() == 5 && b.getMaxY() == 70 && b.getMaxZ() == 4, "max corner is wrong");
        });
        r.add("selection/manager-creates-and-removes", c -> {
            AreaSelectionManager m = DataManager.getAreaSelectionManager();
            String id = m.createNewSelection(testDir(c.mc), "selftest area");
            c.check(id != null, "no selection was created");
            c.check(m.getSelection(id) != null, "the new selection cannot be found");
            c.check(m.removeSelection(id), "the selection was not removed");
        });
        r.add("selection/box-added-to-area", c -> {
            AreaSelectionManager m = DataManager.getAreaSelectionManager();
            m.createNewSelection(testDir(c.mc), "selftest boxes");
            AreaSelection area = m.getCurrentSelection();
            c.check(area != null, "no current selection");
            area.createNewSelectionBox(new BlockPos(0, 64, 0), "first");
            c.check(area.getBoxCount() == 1, "the box count is " + area.getBoxCount() + ", not 1");
            m.removeSelection(area.getName());
        });

        // save/: a schematic is written and read back identical
        r.add("save/write-read-cobblestone", c -> writeAndReadBack(c, "cobblestone_farm.litematic", "save-cobblestone.litematic"));
        r.add("save/write-read-minewave", c -> writeAndReadBack(c, "minewave_worldeater.litematic", "save-minewave.litematic"));

        // lang/: translations load in game (the lang files of the jar reach I18n, and screens show text, not keys)
        r.add("lang/litematica-keys-resolve", c -> checkKeysResolve(c, "litematica"));
        r.add("lang/malilib-keys-resolve", c -> checkKeysResolve(c, "malilib"));

        // preview/: the turning 3D view in the schematic browser
        PreviewTests.register(r);

        // perf/: the speed-ups (they need the player in creative, so they run before the printer tests)
        PerfTests.register(r);

        // printer/, easyplace/, title/ (they set the player to survival, so they run last)
        PrinterTests.register(r);
        MaterialTests.register(r);
        RemoteTests.register(r);
        ServerTests.register(r);
        ConvertTests.register(r);
        RemoteTests.registerLast(r);
        r.waitFor("lang/screen-buttons-show-text", 200, c -> BaseScreen.openScreen(new MainMenuScreen()), c -> {
            if (!(c.mc.currentScreen instanceof MainMenuScreen)) return false;
            // malilib's buttons are GenericButtons in fields of the screen; their label is the translated text
            List<String> labels = new ArrayList<>();
            for (Field field : MainMenuScreen.class.getDeclaredFields()) {
                if (!GenericButton.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                GenericButton button = (GenericButton) field.get(c.mc.currentScreen);
                String label = ReflectionHelper.getPrivateValue(GenericButton.class, button, "fullDisplayString");
                if (label != null) labels.add(label);
            }
            c.check(!labels.isEmpty(), "the main menu has no buttons");
            for (String label : labels) {
                if (label.matches("(litematica|malilib)\\.[a-z0-9_.]+")) throw new AssertionError("a button shows the raw key " + label + "; " + langStatus(c.mc, "litematica"));
            }
            return true;
        });
        // lang/<language>/: the game switched to another of the 13 languages shows our settings screen in it (texts
        // from that language's file of the jar, not English), then goes back to English for the tests after it
        for (String language : new String[] {"de_de", "zh_cn"}) {
            r.waitFor("lang/" + language + "/settings-screen-in-that-language", 400, c -> {
                switchLanguage(c.mc, language);
                BaseScreen.openScreen(ConfigScreen.createOnTab(PrinterInit.TAB));
            }, c -> {
                if (!(c.mc.currentScreen instanceof BaseScreen)) return false;
                for (String[] key : TRANSLATED) {
                    String want = langFile(key[0], language).get(key[1]);
                    String english = langFile(key[0]).get(key[1]);
                    c.check(want != null && !want.equals(english), key[1] + " has no " + language + " translation in the jar");
                    c.check(I18n.format(key[1]).equals(want), key[1] + " shows '" + I18n.format(key[1]) + "', not the " + language + " text '" + want + "'");
                }
                Runner.shot(c.mc, "lang-" + language + "-settings");
                return true;
            });
        }
        r.add("lang/back-to-english", c -> {
            switchLanguage(c.mc, "en_us");
            for (String[] key : TRANSLATED) c.check(I18n.format(key[1]).equals(langFile(key[0]).get(key[1])), key[1] + " is not English again");
        });

        // harness/: the pop-up guard and the watchdog
        r.add("harness/popup-guard-dismisses-pop-up", c -> {
            c.mc.displayGuiScreen(new GuiYesNo(null, "selftest", "a pop-up", 0));
            c.check(c.mc.currentScreen instanceof GuiYesNo, "the pop-up did not open");
            Runner.dismissPopups(c.mc);
            c.check(c.mc.currentScreen == null, "the pop-up guard left the pop-up open");
        });
        r.add("harness/watchdog-fails-hung-test", c -> {
            c.check(Runner.timedOut(5, 5) == null, "a test within its limit was timed out");
            c.check(Runner.timedOut(6, 5) != null, "a test over its limit was not timed out");
        });
    }

    /** The mod's own jar (its file part is the URL of its class, "file:/.../mod.jar!/com/...class"). */
    private static File modJar() throws Exception {
        String entry = LitematicaUpgraded.class.getResource("LitematicaUpgraded.class").getPath();
        return new File(new URL(entry.substring(0, entry.indexOf("!/"))).toURI());
    }

    /** Removes the temporary files the tests made (the game folder's selftest-tmp). */
    static void cleanup(Minecraft mc) {
        deleteTree(new File("selftest-tmp").getAbsoluteFile());
        ConvertTests.cleanup();
        PreviewTests.cleanup();
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteTree(k);
        }
        f.delete();
    }

    /** A region's size with positive sides (Litematica stores a negative size for a flipped region). */
    private static Vec3i absSize(SchematicRegion region) {
        Vec3i size = region.getSize();
        return new Vec3i(Math.abs(size.getX()), Math.abs(size.getY()), Math.abs(size.getZ()));
    }

    /** The names Litematica counts as air (its own BlockState.isAir, the 1.13+ names too). */
    private static final Set<String> AIR = new java.util.HashSet<>(java.util.Arrays.asList(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air"));

    private static Path testDir(Minecraft mc) throws IOException {
        Path dir = Paths.get("selftest-tmp").toAbsolutePath();
        Files.createDirectories(dir);
        return dir;
    }

    /** Copies a test schematic out of the test jar into the game folder and returns where it is. */
    private static Path copyOut(Minecraft mc, String name) throws IOException {
        Path file = testDir(mc).resolve(name);
        try (InputStream in = Tests.class.getResourceAsStream("/selftest/" + name)) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    private static LoadedSchematic load(Minecraft mc, String name) throws IOException {
        Path file = copyOut(mc, name);
        return LoadedSchematic.tryLoadSchematic(file).orElseThrow(() -> new IllegalStateException("cannot load " + name));
    }

    private static NBTTagCompound nbt(Minecraft mc, String name) throws IOException {
        try (InputStream in = Files.newInputStream(testDir(mc).resolve(name))) {
            return CompressedStreamTools.readCompressed(in);
        }
    }

    private static long longTag(NBTTagCompound tag, String key) {
        NBTBase value = tag.getTag(key);
        return value instanceof NBTPrimitive ? ((NBTPrimitive) value).getLong() : Long.MIN_VALUE;
    }

    /** Number of blocks that are not air, counted from the loaded regions. */
    private static long solidCount(Schematic s) {
        long n = 0;
        for (SchematicRegion region : s.getRegions().values()) {
            Vec3i size = absSize(region);
            BlockContainer container = region.getBlockContainer();
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        String name = container.getBlockState(x, y, z).getRegistryNameStr();
                        if (!AIR.contains(name)) n++;
                    }
                }
            }
        }
        return n;
    }

    /** Non-air blocks counted from the file's own palette and packed block states (not the mod's reader). */
    private static long fileSolidCount(NBTTagCompound root) {
        long n = 0;
        NBTTagCompound regions = root.getCompoundTag("Regions");
        for (String key : regions.getKeySet()) {
            NBTTagCompound region = regions.getCompoundTag(key);
            NBTTagList palette = region.getTagList("BlockStatePalette", 10);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.tagCount() - 1));
            NBTTagCompound box = region.getCompoundTag("Size");
            long volume = (long) Math.abs(box.getInteger("x")) * Math.abs(box.getInteger("y")) * Math.abs(box.getInteger("z"));
            long[] data = ((NBTTagLongArrayMixin) region.getTag("BlockStates")).getArray();
            long mask = (1L << bits) - 1;
            for (long i = 0; i < volume; i++) {
                long bit = i * bits;
                int at = (int) (bit >>> 6);
                int off = (int) (bit & 63);
                long v = data[at] >>> off;
                if (off + bits > 64) v |= data[at + 1] << (64 - off);
                if (!AIR.contains(palette.getCompoundTagAt((int) (v & mask)).getString("Name"))) n++;
            }
        }
        return n;
    }

    /** The distinct block names of the schematic with their counts (for the failure messages). */
    private static String names(Schematic s) {
        java.util.TreeMap<String, Integer> counts = new java.util.TreeMap<>();
        for (SchematicRegion region : s.getRegions().values()) {
            Vec3i size = absSize(region);
            BlockContainer container = region.getBlockContainer();
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        counts.merge(container.getBlockState(x, y, z).getRegistryNameStr(), 1, Integer::sum);
                    }
                }
            }
        }
        return counts.toString();
    }

    /** A few block state strings of the schematic, to see what its palette reads as (for the failure messages). */
    private static String sample(Schematic s) {
        StringBuilder out = new StringBuilder();
        for (SchematicRegion region : s.getRegions().values()) {
            BlockContainer container = region.getBlockContainer();
            Vec3i size = absSize(region);
            long volume = (long) size.getX() * size.getY() * size.getZ();
            for (long i = 0; i < volume; i += Math.max(1, volume / 6)) {
                int x = (int) (i % size.getX());
                int y = (int) (i / size.getX() % size.getY());
                int z = (int) (i / ((long) size.getX() * size.getY()));
                out.append(container.getBlockState(x, y, z).getFullStateString()).append("; ");
            }
        }
        return out.toString();
    }

    private static void checkMatchesFile(Runner.Context c, Schematic s, NBTTagCompound root) {
        NBTTagCompound meta = root.getCompoundTag("Metadata");
        NBTTagCompound size = meta.getCompoundTag("EnclosingSize");
        Vec3i enclosing = s.getEnclosingSize();
        c.check(enclosing.getX() == size.getInteger("x") && enclosing.getY() == size.getInteger("y")
                && enclosing.getZ() == size.getInteger("z"), "enclosing size " + enclosing + " differs from the file");
        c.check(s.getRegions().size() == meta.getInteger("RegionCount"), "region count differs from the file");
    }

    private static void writeAndReadBack(Runner.Context c, String name, String out) throws IOException {
        LoadedSchematic loaded = load(c.mc, name);
        Path file = testDir(c.mc).resolve(out);
        c.check(SchematicFileUtils.writeToFile(loaded.schematic, file, true), "the schematic was not written");
        LoadedSchematic back = LoadedSchematic.tryLoadSchematic(file).orElseThrow(() -> new IllegalStateException("cannot read " + out));
        Schematic a = loaded.schematic;
        Schematic b = back.schematic;
        c.check(a.getEnclosingSize().equals(b.getEnclosingSize()), "enclosing size changed on save");
        c.check(a.getRegions().size() == b.getRegions().size(), "region count changed on save");
        c.check(solidCount(a) == solidCount(b), "block count changed on save: " + solidCount(a) + " -> " + solidCount(b));
    }

    /** The Mixin class names a config lists in its json (mixins, client and server lists). */
    /** The classes a mixin targets, read from its @Mixin annotation in the jar (the mixin class itself must never be loaded). */
    private static List<String> mixinTargets(String mixin) throws IOException {
        List<String> targets = new ArrayList<>();
        try (InputStream in = Tests.class.getResourceAsStream("/" + mixin.replace('.', '/') + ".class")) {
            new org.objectweb.asm.ClassReader(readAll(in)).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM5) {
                @Override
                public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(desc)) return null;
                    return new org.objectweb.asm.AnnotationVisitor(org.objectweb.asm.Opcodes.ASM5) {
                        @Override
                        public org.objectweb.asm.AnnotationVisitor visitArray(String name) {
                            return new org.objectweb.asm.AnnotationVisitor(org.objectweb.asm.Opcodes.ASM5) {
                                @Override
                                public void visit(String n, Object v) {
                                    if (v instanceof org.objectweb.asm.Type) targets.add(((org.objectweb.asm.Type) v).getClassName());
                                    else if (v instanceof String) targets.add(((String) v).replace('/', '.'));
                                }
                            };
                        }
                    };
                }
            }, org.objectweb.asm.ClassReader.SKIP_CODE);
        }
        return targets;
    }

    private static Set<String> mixinClasses(String config) throws IOException {
        JsonObject json;
        try (InputStream in = Tests.class.getResourceAsStream("/" + config)) {
            json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String pkg = json.get("package").getAsString();
        Set<String> names = new java.util.HashSet<>();
        for (String key : new String[] {"mixins", "client", "server"}) {
            if (!json.has(key)) continue;
            JsonArray list = json.getAsJsonArray(key);
            for (int i = 0; i < list.size(); i++) names.add(pkg + "." + list.get(i).getAsString());
        }
        return names;
    }

    /** Every key of the domain's en_us.lang (as the jar has it) resolves through I18n to its English text. */
    private static void checkKeysResolve(Runner.Context c, String domain) throws Exception {
        Map<String, String> lang = langFile(domain);
        c.check(lang.size() > 100, domain + " has only " + lang.size() + " keys in its en_us.lang");
        List<String> unresolved = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> entry : lang.entrySet()) {
            if (!I18n.hasKey(entry.getKey())) unresolved.add(entry.getKey());
            else if (!entry.getValue().contains("%") && !I18n.format(entry.getKey()).equals(entry.getValue())) wrong.add(entry.getKey());
        }
        c.check(unresolved.isEmpty() && wrong.isEmpty(), unresolved.size() + " keys not known to I18n (first: " + first(unresolved) + "), "
                + wrong.size() + " with other text (first: " + first(wrong) + "); " + langStatus(c.mc, domain));
    }

    /** The domain's en_us.lang as the mod jar has it: key to English text. */
    /** One key of each lang domain the language tests compare: our printer setting, Litematica's and malilib's texts. */
    private static final String[][] TRANSLATED = {{"litematica_upgraded", "litematica_upgraded.config.name.printerenabled"},
            {"litematica", "litematica.button.change_menu.schematic_manager"}, {"malilib", "malilib.button.config.reset_all_filtered"}};

    /** Switches the game's language the way the language screen does, reloading only the translations. */
    private static void switchLanguage(Minecraft mc, String code) {
        mc.gameSettings.language = code;
        mc.getLanguageManager().setCurrentLanguage(new net.minecraft.client.resources.Language(code, "", "", false));
        mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        mc.fontRenderer.setUnicodeFlag(mc.getLanguageManager().isCurrentLocaleUnicode() || mc.gameSettings.forceUnicodeFont);
    }

    private static Map<String, String> langFile(String domain) throws Exception {
        return langFile(domain, "en_us");
    }

    private static Map<String, String> langFile(String domain, String language) throws Exception {
        try (JarFile jf = new JarFile(modJar())) {
            JarEntry entry = jf.getJarEntry("assets/" + domain + "/lang/" + language + ".lang");
            if (entry == null) throw new AssertionError("the jar has no assets/" + domain + "/lang/" + language + ".lang");
            Map<String, String> lang = new LinkedHashMap<>();
            for (String line : new String(readAll(jf.getInputStream(entry)), StandardCharsets.UTF_8).split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) lang.put(line.substring(0, eq), line.substring(eq + 1).replace("\r", ""));
            }
            return lang;
        }
    }

    /** What the game's translation setup looks like now, for the failure message of a lang test. */
    private static String langStatus(Minecraft mc, String domain) throws Exception {
        StringBuilder status = new StringBuilder("language " + mc.getLanguageManager().getCurrentLanguage().getLanguageCode());
        status.append(", resource domains ").append(mc.getResourceManager().getResourceDomains());
        for (String name : new String[] {"minecraft", domain}) {
            try {
                status.append(", ").append(name).append(" en_us.lang packs ")
                        .append(mc.getResourceManager().getAllResources(new ResourceLocation(name, "lang/en_us.lang")).size());
            } catch (IOException e) {
                status.append(", ").append(name).append(" en_us.lang: ").append(e);
            }
        }
        return status.toString();
    }

    private static String first(List<String> items) {
        return items.isEmpty() ? "-" : items.get(0);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
