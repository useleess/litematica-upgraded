package com.useleess.litematicaupgraded.selftest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.scheduler.TaskScheduler;
import com.useleess.litematicaupgraded.litematica.scheduler.task.LocalCreateSchematicTask;
import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskFillArea;
import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskPasteSchematicPerChunkCommand;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.Schematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicSaveSettings;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.data.EntityData;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.util.SchematicFileUtils;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelection;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelectionManager;
import com.useleess.litematicaupgraded.litematica.selection.SelectionBox;
import com.useleess.litematicaupgraded.litematica.util.ToolUtils;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.converter.DataConverterNbt;
import com.useleess.litematicaupgraded.remote.RemoteTasks;
import com.useleess.litematicaupgraded.server.RemoteBlob;
import com.useleess.litematicaupgraded.server.RemoteClient;
import com.useleess.litematicaupgraded.server.ServerFeatures;
import com.useleess.litematicaupgraded.server.ServerJobs;
import com.useleess.litematicaupgraded.server.StateStrings;
import com.useleess.litematicaupgraded.server.net.MessageEditStatus;
import com.useleess.litematicaupgraded.server.net.MessageEditUpload;
import com.useleess.litematicaupgraded.server.net.MessageSaveRequest;
import com.useleess.litematicaupgraded.server.net.Net;

/**
 * server/save, paste, edit, move, permission, limits: Litematica's own save / paste / fill / replace / delete / move through the
 * server part. Each test builds its scene with server commands on its own site, runs the real Litematica task or tool, and
 * checks the real world afterwards: blocks in the client world, container items through the server (/testforblock), sign
 * text and entities in the client world. Requests that Litematica itself would not make (to see the server refuse them) are
 * sent raw.
 */
final class RemoteTests {
    private RemoteTests() {}

    private static final IBlockState STONE = Blocks.STONE.getDefaultState();
    private static final IBlockState GLASS = Blocks.GLASS.getDefaultState();
    private static final IBlockState DIRT = Blocks.DIRT.getDefaultState();
    private static final int Y = 100;
    private static final String ITEMS = "{Items:[{Slot:3b,id:\"minecraft:diamond\",Count:5b,Damage:0s},{Slot:10b,id:\"minecraft:iron_ingot\",Count:12b,Damage:0s}]}";

    private static int siteCounter, baseX, baseZ;
    /** What the first save test brought back, for the one that writes it to a file. */
    private static Schematic lastSaved;

    // ---- a test as a list of steps, one step per tick at most ----

    private interface Step {
        /** True when the step is done and the next one may run. */
        boolean run(Runner.Context c) throws Exception;
    }

    private static final class Flow {
        private final List<Step> steps = new ArrayList<>();
        private int at;

        Flow step(Step step) {
            steps.add(step);
            return this;
        }

        Flow run(Consumer<Runner.Context> action) {
            return step(c -> {
                action.accept(c);
                return true;
            });
        }

        Flow cmd(String text) {
            return run(c -> c.mc.player.sendChatMessage("/" + text));
        }

        Flow pause(int ticks) {
            int[] n = {0};
            return step(c -> ++n[0] >= ticks);
        }

        Flow until(String what, int maxTicks, Predicate<Runner.Context> condition) {
            int[] n = {0};
            return step(c -> {
                if (condition.test(c)) return true;
                if (++n[0] > maxTicks) throw new AssertionError("timed out waiting for " + what + " (" + maxTicks + " ticks)");
                return false;
            });
        }

        /** Waits until the function returns null (nothing wrong); the last problem is the failure when it never does. */
        Flow untilOk(String what, int maxTicks, Function<Runner.Context, String> problem) {
            int[] n = {0};
            String[] last = {"never checked"};
            return step(c -> {
                last[0] = problem.apply(c);
                if (last[0] == null) return true;
                if (++n[0] > maxTicks) throw new AssertionError("timed out waiting for " + what + " (" + maxTicks + " ticks): " + last[0]);
                return false;
            });
        }

        /** Sends a command and waits for the chat line with the text. */
        Flow expectChat(String command, String needle, String what) {
            step(c -> {
                ServerTests.CHAT.clear();
                c.mc.player.sendChatMessage("/" + command);
                return true;
            });
            return until(what, 200, c -> {
                if (ServerTests.chatHas(needle)) return true;
                if (ServerTests.chatHas("The block at")) throw new AssertionError(what + ": " + ServerTests.CHAT);
                return false;
            });
        }

        boolean poll(Runner.Context c) throws Exception {
            if (at >= steps.size()) return true;
            if (steps.get(at).run(c)) at++;
            return at >= steps.size();
        }
    }

    /** Registers a test whose steps are built when the test starts (so they can use where the player is then). */
    private static void flow(Runner.Registry r, String id, int maxTicks, Function<Runner.Context, Flow> build) {
        Flow[] holder = {null};
        r.waitFor(id, maxTicks, c -> holder[0] = build.apply(c), c -> holder[0].poll(c));
    }

    // ---- sites and scenes ----

    private static final class Site {
        final int x, z;

        Site(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private static Site nextSite(Runner.Context c) {
        if (siteCounter == 0) {
            baseX = (int) Math.floor(c.mc.player.posX) + 24;
            baseZ = (int) Math.floor(c.mc.player.posZ) + 70;
        }
        int k = siteCounter++;
        return new Site(baseX + (k % 4) * 40, baseZ + (k / 4) * 40);
    }

    private static Block blockAt(Runner.Context c, int x, int y, int z) {
        return c.mc.world.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    /** A creative player on a stone platform in the sky, air above it, nothing else around. */
    private static Flow prep(Flow f, Site s) {
        return f.cmd("gamemode creative")
            .cmd("fill " + (s.x - 2) + " " + Y + " " + (s.z - 2) + " " + (s.x + 29) + " " + (Y + 12) + " " + (s.z + 29) + " minecraft:air")
            .cmd("fill " + (s.x - 2) + " " + (Y - 1) + " " + (s.z - 2) + " " + (s.x + 29) + " " + (Y - 1) + " " + (s.z + 29) + " minecraft:stone")
            // on the platform (outside every test area, which start at s.z): a player next to it would fall and could die in survival later
            .cmd("tp @s " + (s.x + 2.5) + " " + Y + " " + (s.z - 0.5) + " 0 0")
            .cmd("kill @e[type=!player,x=" + (s.x + 14) + ",y=" + Y + ",z=" + (s.z + 14) + ",r=60]")
            .untilOk("the platform", 400, c -> {
                if (blockAt(c, s.x + 3, Y - 1, s.z + 3) != Blocks.STONE) return "no platform yet";
                if (blockAt(c, s.x + 3, Y + 5, s.z + 3) != Blocks.AIR) return "the air above is not there yet";
                double dx = c.mc.player.posX - (s.x + 2.5), dz = c.mc.player.posZ - (s.z - 0.5);
                if (dx * dx + dz * dz > 1 || Math.abs(c.mc.player.posY - Y) > 0.5) return "the player is not at the site yet";
                return c.mc.player.onGround ? null : "the player does not stand on the platform yet";
            });
    }

    private static String q(String json) {
        return json.replace("\"", "\\\"");
    }

    /** The scene of the save tests, built from its minimum corner: a chest with items, stone, a sign, stairs, an armor stand. 7 x 3 x 4. */
    private static Flow scene(Flow f, int x, int y, int z) {
        return f.cmd("setblock " + (x + 1) + " " + y + " " + (z + 1) + " minecraft:chest 2 replace " + ITEMS)
            .cmd("setblock " + (x + 2) + " " + y + " " + (z + 1) + " minecraft:stone")
            .cmd("setblock " + (x + 3) + " " + y + " " + (z + 1) + " minecraft:standing_sign 4 replace {Text1:\"" + q("{\"text\":\"LU save\"}") + "\"}")
            .cmd("setblock " + (x + 2) + " " + (y + 1) + " " + (z + 1) + " minecraft:stone_stairs 1")
            .cmd("summon minecraft:armor_stand " + (x + 5.5) + " " + y + " " + (z + 1.5) + " {CustomName:\"LU-stand\",NoGravity:1b}");
    }

    private static String sceneProblem(Runner.Context c, int x, int y, int z) {
        if (blockAt(c, x + 1, y, z + 1) != Blocks.CHEST) return "no chest at " + (x + 1) + "," + y + "," + (z + 1) + ", there is " + blockAt(c, x + 1, y, z + 1);
        if (blockAt(c, x + 2, y, z + 1) != Blocks.STONE) return "no stone";
        if (blockAt(c, x + 2, y + 1, z + 1) != Blocks.STONE_STAIRS) return "no stairs";
        if (blockAt(c, x + 3, y, z + 1) != Blocks.STANDING_SIGN) return "no sign";
        TileEntity te = c.mc.world.getTileEntity(new BlockPos(x + 3, y, z + 1));
        if (!(te instanceof TileEntitySign)) return "the sign has no tile entity on the client";
        String text = ((TileEntitySign) te).signText[0].getUnformattedText();
        if (!"LU save".equals(text)) return "the sign says '" + text + "'";
        List<EntityArmorStand> stands = c.mc.world.getEntitiesWithinAABB(EntityArmorStand.class, new AxisAlignedBB(x, y, z, x + 7, y + 3, z + 4));
        if (stands.size() != 1) return stands.size() + " armor stands";
        if (!"LU-stand".equals(stands.get(0).getCustomNameTag())) return "the armor stand is called '" + stands.get(0).getCustomNameTag() + "'";
        return null;
    }

    private static Flow chestItems(Flow f, int x, int y, int z) {
        return f.expectChat("testforblock " + x + " " + y + " " + z + " minecraft:chest -1 {Items:[{Slot:3b,id:\"minecraft:diamond\",Count:5b},{Slot:10b,id:\"minecraft:iron_ingot\",Count:12b}]}",
            "Successfully found the block", "the chest at " + x + "," + y + "," + z + " has its items");
    }

    private static String airProblem(Runner.Context c, int x, int y, int z, int sx, int sy, int sz) {
        for (int dy = 0; dy < sy; dy++) for (int dz = 0; dz < sz; dz++) for (int dx = 0; dx < sx; dx++) {
            if (blockAt(c, x + dx, y + dy, z + dz) != Blocks.AIR) return "not air at " + (x + dx) + "," + (y + dy) + "," + (z + dz) + ": " + blockAt(c, x + dx, y + dy, z + dz);
        }
        if (!c.mc.world.getEntitiesWithinAABB(EntityArmorStand.class, new AxisAlignedBB(x, y, z, x + sx, y + sy, z + sz)).isEmpty()) return "an armor stand is still there";
        if (!c.mc.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(x - 2, y - 2, z - 2, x + sx + 2, y + sy + 2, z + sz + 2)).isEmpty()) return "dropped items lie around";
        return null;
    }

    private static com.useleess.litematicaupgraded.malilib.util.position.BlockPos lb(int x, int y, int z) {
        return new com.useleess.litematicaupgraded.malilib.util.position.BlockPos(x, y, z);
    }

    private static AreaSelection area(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        AreaSelection area = new AreaSelection();
        area.setName(name);
        area.addSelectionBox(new SelectionBox(lb(x1, y1, z1), lb(x2, y2, z2), "lu"), false);
        area.setManualOrigin(lb(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)));
        return area;
    }

    private static Path dir() throws java.io.IOException {
        Path dir = Paths.get("selftest-tmp").toAbsolutePath();
        Files.createDirectories(dir);
        return dir;
    }

    /** Whether {@link #select} switched Litematica out of its simple selection mode (unselect switches back). */
    private static boolean switchedMode;

    /** Makes the area the current selection (what the fill and move tools use). */
    private static String select(Runner.Context c, String name, int x1, int y1, int z1, int x2, int y2, int z2) throws java.io.IOException {
        AreaSelectionManager m = DataManager.getAreaSelectionManager();
        // in the simple mode (the default) the current selection is the one simple area, not the new selection
        if (m.getSelectionMode() == com.useleess.litematicaupgraded.litematica.selection.AreaSelectionType.SIMPLE) {
            m.switchSelectionMode();
            switchedMode = true;
        }
        String id = m.createNewSelection(dir(), name);
        AreaSelection area = m.getCurrentSelection();
        c.check(area != null, "no current selection");
        // a new selection starts with a box of its own (at 0, 0, 0): only ours is left
        area.removeAllSelectionBoxes();
        area.addSelectionBox(new SelectionBox(lb(x1, y1, z1), lb(x2, y2, z2), "lu"), false);
        area.setSelectedSelectionBox("lu");
        area.setManualOrigin(lb(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)));
        return id;
    }

    private static void unselect(String id) {
        AreaSelectionManager m = DataManager.getAreaSelectionManager();
        m.removeSelection(id);
        if (switchedMode && m.getSelectionMode() == com.useleess.litematicaupgraded.litematica.selection.AreaSelectionType.MULTI_REGION) m.switchSelectionMode();
        switchedMode = false;
    }

    // ---- reading a saved schematic ----

    private static SchematicRegion only(Schematic schematic) {
        return schematic.getRegions().values().iterator().next();
    }

    private static NBTTagCompound vanilla(CompoundData data) {
        return DataConverterNbt.toVanillaCompound(data);
    }

    /** The scene of {@link #scene} as a saved schematic: blocks, container items, sign text, the armor stand. */
    private static void verifySaved(Runner.Context c, Schematic schematic) {
        SchematicRegion region = only(schematic);
        BlockContainer blocks = region.getBlockContainer();
        c.check(blocks.getBlockState(1, 0, 1).vanillaState().getBlock() == Blocks.CHEST, "no chest in the saved schematic");
        c.check(blocks.getBlockState(2, 0, 1).vanillaState().getBlock() == Blocks.STONE, "no stone in the saved schematic");
        c.check(blocks.getBlockState(2, 1, 1).vanillaState().getBlock() == Blocks.STONE_STAIRS, "no stairs in the saved schematic");
        c.check(blocks.getBlockState(3, 0, 1).vanillaState().getBlock() == Blocks.STANDING_SIGN, "no sign in the saved schematic");
        int solid = 0;
        for (int y = 0; y < 3; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 7; x++) {
            if (blocks.getBlockState(x, y, z).vanillaState().getBlock() != Blocks.AIR) solid++;
        }
        c.check(solid == 4, solid + " blocks in the saved schematic, wanted 4");
        CompoundData chest = region.getBlockEntityMap().get(lb(1, 0, 1));
        c.check(chest != null, "no chest data in the saved schematic: " + region.getBlockEntityMap().keySet());
        NBTTagList items = vanilla(chest).getTagList("Items", 10);
        c.check(items.tagCount() == 2, "the chest has " + items.tagCount() + " item stacks in the saved schematic");
        boolean diamond = false, iron = false;
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound stack = items.getCompoundTagAt(i);
            diamond |= stack.getByte("Slot") == 3 && "minecraft:diamond".equals(stack.getString("id")) && stack.getByte("Count") == 5;
            iron |= stack.getByte("Slot") == 10 && "minecraft:iron_ingot".equals(stack.getString("id")) && stack.getByte("Count") == 12;
        }
        c.check(diamond && iron, "the chest items are wrong in the saved schematic: " + items);
        CompoundData sign = region.getBlockEntityMap().get(lb(3, 0, 1));
        c.check(sign != null && vanilla(sign).getString("Text1").contains("LU save"), "the sign text is wrong in the saved schematic");
        List<EntityData> entities = region.getEntityList();
        c.check(entities.size() == 1, entities.size() + " entities in the saved schematic, wanted the armor stand");
        EntityData stand = entities.get(0);
        c.check("LU-stand".equals(vanilla(stand.data).getString("CustomName")), "the armor stand lost its name: " + vanilla(stand.data));
        c.check(Math.abs(stand.pos.x - 5.5) < 0.01 && Math.abs(stand.pos.y) < 0.01 && Math.abs(stand.pos.z - 1.5) < 0.01, "the armor stand is at " + stand.pos);
    }

    /** Runs Litematica's save of the box through the scheduler; the schematic lands in out[0]. */
    private static Flow save(Flow f, String name, int x, int y, int z, Schematic[] out, int[] before) {
        return f.run(c -> before[0] = RemoteTasks.saved)
            .run(c -> TaskScheduler.getInstanceClient().scheduleTask(
                new LocalCreateSchematicTask(area(name, x, y, z, x + 6, y + 2, z + 3), new SchematicSaveSettings(), s -> out[0] = s), 1))
            .until("the saved schematic", 800, c -> out[0] != null)
            .run(c -> c.check(RemoteTasks.saved == before[0] + 1, "the save was not done by the server (server saves " + RemoteTasks.saved + ", before " + before[0] + ")"));
    }

    private static boolean chunkLoaded(Runner.Context c, int x, int z) {
        return c.mc.world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4) != null;
    }

    // ---- pasting ----

    private static NBTTagCompound item(int slot, String id, int count) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Slot", (byte) slot);
        tag.setString("id", id);
        tag.setByte("Count", (byte) count);
        tag.setShort("Damage", (short) 0);
        return tag;
    }

    /** Loads the file as a placement at the position and waits until the schematic world has it. */
    private static SchematicPlacement[] place(Flow f, Path[] file, int x, int y, int z, BlockPos probe, Block probeBlock) {
        SchematicPlacement[] placement = {null};
        f.run(c -> {
            LoadedSchematic loaded = LoadedSchematic.tryLoadSchematic(file[0]).orElseThrow(() -> new IllegalStateException("cannot load the test schematic"));
            placement[0] = SchematicPlacement.create(loaded, lb(x, y, z), "selftest-remote", true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement[0], false);
        }).untilOk("the placement in the schematic world", 400, c -> {
            net.minecraft.world.World schematicWorld = SchematicWorldHandler.getSchematicWorld();
            return schematicWorld != null && schematicWorld.getBlockState(probe).getBlock() == probeBlock ? null : "the schematic world lacks the probe block";
        });
        return placement;
    }

    private static void pasteTask(SchematicPlacement placement) {
        TaskScheduler.getInstanceClient().scheduleTask(
            new TaskPasteSchematicPerChunkCommand(ImmutableList.of(placement), DataManager.getRenderLayerRange().copy(), false), 1);
    }

    private static void removePlacement(SchematicPlacement[] placement) {
        if (placement[0] != null) DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement[0], true);
    }

    /** Litematica's own (command) paste of stone, stairs and dirt at the site; the three blocks must stand there at the end. */
    private static Flow ownPaste(Flow f, Site s, String name) {
        Path[] file = {null};
        int ox = s.x + 4, oz = s.z + 14;
        f.step(c -> {
            Map<BlockPos, IBlockState> build = new LinkedHashMap<>();
            build.put(new BlockPos(0, 0, 0), STONE);
            build.put(new BlockPos(1, 0, 0), Blocks.STONE_STAIRS.getDefaultState());
            build.put(new BlockPos(2, 0, 0), DIRT);
            file[0] = dir().resolve(name + ".litematic");
            LitematicWriter.write(file[0], build);
            return true;
        });
        SchematicPlacement[] placement = place(f, file, ox, Y, oz, new BlockPos(ox, Y, oz), Blocks.STONE);
        return f.run(c -> pasteTask(placement[0]))
            .untilOk("the blocks pasted with Litematica's own commands", 800, c -> blockAt(c, ox, Y, oz) == Blocks.STONE
                && blockAt(c, ox + 1, Y, oz) == Blocks.STONE_STAIRS && blockAt(c, ox + 2, Y, oz) == Blocks.DIRT ? null : "the blocks are not all there yet")
            .cmd("gamerule sendCommandFeedback true")
            .run(c -> removePlacement(placement));
    }

    // ---- raw requests: what Litematica would not send, to see the server refuse it ----

    private static RemoteClient.Remote rawFill(Runner.Context c, int x, int y, int z) throws Exception {
        NBTTagCompound blob = new NBTTagCompound();
        blob.setString("op", RemoteBlob.OP_FILL);
        NBTTagList boxes = new NBTTagList();
        boxes.appendTag(new NBTTagIntArray(new int[] {x, y, z, x + 2, y, z + 2}));
        blob.setTag("boxes", boxes);
        blob.setString("fill", StateStrings.toString(GLASS));
        blob.setString("replace", "");
        blob.setBoolean("entities", false);
        RemoteClient.Remote remote = RemoteClient.begin();
        RemoteClient.upload(remote, c.mc.player.dimension, RemoteBlob.gzip(blob));
        return remote;
    }

    private static RemoteClient.Remote rawAnnounce(Runner.Context c, int count) {
        RemoteClient.Remote remote = RemoteClient.begin();
        Net.CHANNEL.sendToServer(new MessageEditUpload(remote.id, c.mc.player.dimension, 0, count, new byte[10]));
        return remote;
    }

    private static RemoteClient.Remote rawSave(Runner.Context c, int x, int y, int z) {
        RemoteClient.Remote remote = RemoteClient.begin();
        List<MessageSaveRequest.Box> boxes = new ArrayList<>();
        boxes.add(new MessageSaveRequest.Box("lu", x, y, z, x + 2, y, z + 2));
        Net.CHANNEL.sendToServer(new MessageSaveRequest(remote.id, c.mc.player.dimension, 7, boxes));
        return remote;
    }

    private static void refusedWith(Runner.Context c, RemoteClient.Remote remote, String key) {
        c.check(remote.state == MessageEditStatus.REFUSED, "the server did not refuse (state " + remote.state + ")");
        c.check(key.equals(remote.key), "the refusal is '" + remote.key + "', wanted '" + key + "'");
        c.check(!StringUtils.translate(key).equals(key), "the refusal text is not translated: " + key);
    }

    private static int count(Runner.Context c, Block block, int x, int y, int z, int sx, int sy, int sz) {
        int n = 0;
        for (int dy = 0; dy < sy; dy++) for (int dz = 0; dz < sz; dz++) for (int dx = 0; dx < sx; dx++) if (blockAt(c, x + dx, y + dy, z + dz) == block) n++;
        return n;
    }

    private static String allOf(Runner.Context c, Block block, int x, int y, int z, int sx, int sy, int sz) {
        int n = count(c, block, x, y, z, sx, sy, sz);
        return n == sx * sy * sz ? null : n + " of " + sx * sy * sz + " blocks are " + block.getRegistryName();
    }

    // ---- the tests ----

    private static final int AREA = 30, AREA_HIGH = 5;

    /** A fill of a big area, started like Litematica's own tool does (but polled every tick): the task. */
    private static TaskFillArea bigFill(Site s) {
        return new TaskFillArea(ImmutableList.of(new SelectionBox(lb(s.x, Y, s.z), lb(s.x + AREA - 1, Y + AREA_HIGH - 1, s.z + AREA - 1), "lu")), GLASS, null, false);
    }

    /** Waits until the job started after {@code old} is accepted by the server; the job goes into holder[0]. */
    private static Flow accepted(Flow f, RemoteClient.Remote[] old, RemoteClient.Remote[] holder) {
        return f.until("the server to accept the job", 300, c -> {
            RemoteClient.Remote last = RemoteTasks.lastRemote;
            if (last == null || last == old[0] || last.state < MessageEditStatus.ACCEPTED) return false;
            holder[0] = last;
            return true;
        });
    }

    static void register(Runner.Registry r) {
        // ---- server/save/ ----
        flow(r, "server/save/blocks-nbt-and-entities-of-a-loaded-area", 3000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            scene(f, s.x, Y, s.z).untilOk("the scene", 300, cc -> sceneProblem(cc, s.x, Y, s.z));
            Schematic[] out = {null};
            save(f, "lu save", s.x, Y, s.z, out, new int[1]);
            return f.run(cc -> {
                verifySaved(cc, out[0]);
                lastSaved = out[0];
            });
        });
        flow(r, "server/save/area-the-client-has-not-loaded", 4000, c -> {
            Site s = nextSite(c);
            int fx = s.x + 320, fz = s.z;
            Flow f = prep(new Flow(), s);
            f.cmd("tp @s " + (fx + 2) + " " + Y + " " + (fz - 3) + " 0 0")
                .until("the far chunks on the client", 600, cc -> chunkLoaded(cc, fx, fz) && chunkLoaded(cc, fx + 7, fz + 4))
                .cmd("fill " + (fx - 2) + " " + (Y - 1) + " " + (fz - 2) + " " + (fx + 9) + " " + (Y - 1) + " " + (fz + 6) + " minecraft:stone");
            scene(f, fx, Y, fz).untilOk("the far scene", 400, cc -> sceneProblem(cc, fx, Y, fz))
                .cmd("tp @s " + (s.x + 2) + " " + Y + " " + (s.z - 3) + " 0 0")
                .until("the far chunks to be gone from the client", 600, cc -> !chunkLoaded(cc, fx, fz) && !chunkLoaded(cc, fx + 7, fz + 4));
            Schematic[] out = {null};
            save(f, "lu far", fx, Y, fz, out, new int[1]);
            return f.run(cc -> verifySaved(cc, out[0]));
        });
        flow(r, "server/save/saved-file-reads-back-identical", 600, c -> new Flow().step(cc -> {
            cc.check(lastSaved != null, "the first save test left no schematic");
            Path file = dir().resolve("server-save.litematic");
            cc.check(SchematicFileUtils.writeToFile(lastSaved, file, true), "the schematic was not written");
            LoadedSchematic back = LoadedSchematic.tryLoadSchematic(file).orElseThrow(() -> new IllegalStateException("cannot read the saved file"));
            cc.check(back.schematic.getRegions().size() == lastSaved.getRegions().size(), "the region count changed on save");
            cc.check(back.schematic.getEnclosingSize().equals(lastSaved.getEnclosingSize()), "the enclosing size changed on save");
            verifySaved(cc, back.schematic);
            Files.deleteIfExists(file);
            return true;
        }));

        // ---- server/paste/ ----
        flow(r, "server/paste/container-items-and-sign-text", 3000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            Path[] file = {null};
            int ox = s.x + 4, oz = s.z + 14;
            int[] before = {0};
            f.step(cc -> {
                Map<BlockPos, IBlockState> build = new LinkedHashMap<>();
                Map<BlockPos, NBTTagCompound> tiles = new LinkedHashMap<>();
                build.put(new BlockPos(0, 0, 0), Blocks.CHEST.getDefaultState());
                NBTTagCompound chest = new NBTTagCompound();
                chest.setString("id", "minecraft:chest");
                NBTTagList list = new NBTTagList();
                list.appendTag(item(3, "minecraft:diamond", 5));
                list.appendTag(item(10, "minecraft:iron_ingot", 12));
                chest.setTag("Items", list);
                tiles.put(new BlockPos(0, 0, 0), chest);
                build.put(new BlockPos(1, 0, 0), STONE);
                build.put(new BlockPos(2, 0, 0), Blocks.STANDING_SIGN.getDefaultState());
                NBTTagCompound sign = new NBTTagCompound();
                sign.setString("id", "minecraft:sign");
                sign.setString("Text1", "{\"text\":\"LU paste\"}");
                tiles.put(new BlockPos(2, 0, 0), sign);
                file[0] = dir().resolve("server-paste.litematic");
                LitematicWriter.write(file[0], build, tiles);
                before[0] = RemoteTasks.edited;
                return true;
            });
            SchematicPlacement[] placement = place(f, file, ox, Y, oz, new BlockPos(ox, Y, oz), Blocks.CHEST);
            f.run(cc -> pasteTask(placement[0]))
                .until("the server to finish the paste", 800, cc -> RemoteTasks.edited > before[0])
                .untilOk("the pasted blocks", 300, cc -> {
                    if (blockAt(cc, ox, Y, oz) != Blocks.CHEST) return "no chest";
                    if (blockAt(cc, ox + 1, Y, oz) != Blocks.STONE) return "no stone";
                    TileEntity te = cc.mc.world.getTileEntity(new BlockPos(ox + 2, Y, oz));
                    if (!(te instanceof TileEntitySign)) return "no sign";
                    String text = ((TileEntitySign) te).signText[0].getUnformattedText();
                    return "LU paste".equals(text) ? null : "the sign says '" + text + "'";
                });
            chestItems(f, ox, Y, oz).run(cc -> removePlacement(placement));
            return f;
        });
        flow(r, "server/paste/save-then-paste-copies-blocks-and-entities", 4000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            scene(f, s.x, Y, s.z).untilOk("the scene", 300, cc -> sceneProblem(cc, s.x, Y, s.z));
            Schematic[] out = {null};
            save(f, "lu copy", s.x, Y, s.z, out, new int[1]);
            int bx = s.x + 12, bz = s.z;
            SchematicPlacement[] placement = {null};
            int[] before = {0};
            f.run(cc -> {
                placement[0] = SchematicPlacement.create(new LoadedSchematic(out[0]), lb(bx, Y, bz), "selftest-copy", true);
                DataManager.getSchematicPlacementManager().addSchematicPlacement(placement[0], false);
                before[0] = RemoteTasks.edited;
            }).untilOk("the copy in the schematic world", 400, cc -> {
                net.minecraft.world.World world = SchematicWorldHandler.getSchematicWorld();
                return world != null && world.getBlockState(new BlockPos(bx + 1, Y, bz + 1)).getBlock() == Blocks.CHEST ? null : "the schematic world lacks the copy";
            }).run(cc -> pasteTask(placement[0]))
                .until("the server to finish the paste", 800, cc -> RemoteTasks.edited > before[0])
                .untilOk("the copy", 300, cc -> sceneProblem(cc, bx, Y, bz));
            chestItems(f, bx + 1, Y, bz + 1);
            return f.run(cc -> {
                for (int dy = 0; dy < 3; dy++) for (int dz = 0; dz < 4; dz++) for (int dx = 0; dx < 7; dx++) {
                    IBlockState a = cc.mc.world.getBlockState(new BlockPos(s.x + dx, Y + dy, s.z + dz));
                    IBlockState b = cc.mc.world.getBlockState(new BlockPos(bx + dx, Y + dy, bz + dz));
                    cc.check(a.getBlock() == b.getBlock() && a.getBlock().getMetaFromState(a) == b.getBlock().getMetaFromState(b),
                        "the copy differs at " + dx + "," + dy + "," + dz + ": " + a + " vs " + b);
                }
                removePlacement(placement);
            });
        });
        flow(r, "server/paste/big-paste-is-spread-over-ticks", 4000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            Path[] file = {null};
            int ox = s.x + 2, oz = s.z + 4;
            int[] before = {0, 0};
            long[] startTick = {0};
            f.cmd("litematicaupgraded edits rate 200").step(cc -> {
                Map<BlockPos, IBlockState> build = new LinkedHashMap<>();
                for (int y = 0; y < 8; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) build.put(new BlockPos(x, y, z), (x + y + z) % 2 == 0 ? STONE : DIRT);
                file[0] = dir().resolve("server-big.litematic");
                LitematicWriter.write(file[0], build);
                return true;
            });
            SchematicPlacement[] placement = place(f, file, ox, Y, oz, new BlockPos(ox, Y, oz), Blocks.STONE);
            f.run(cc -> {
                before[0] = RemoteTasks.edited;
                before[1] = RemoteTasks.progressSeen;
                startTick[0] = cc.mc.world.getTotalWorldTime();
                pasteTask(placement[0]);
            }).until("the server to finish the big paste", 1500, cc -> RemoteTasks.edited > before[0])
                .run(cc -> {
                    long ticks = cc.mc.world.getTotalWorldTime() - startTick[0];
                    cc.check(RemoteTasks.progressSeen > before[1], "no progress of the server was shown");
                    cc.check(ticks >= 8, "2048 blocks at 200 per tick took only " + ticks + " ticks: the paste was not spread over ticks");
                })
                .untilOk("every block of the big paste", 300, cc -> {
                    int wrong = 0;
                    for (int y = 0; y < 8; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        Block want = (x + y + z) % 2 == 0 ? Blocks.STONE : Blocks.DIRT;
                        if (blockAt(cc, ox + x, Y + y, oz + z) != want) wrong++;
                    }
                    return wrong == 0 ? null : wrong + " of 2048 blocks are wrong";
                })
                .cmd("litematicaupgraded edits rate 20000")
                .run(cc -> removePlacement(placement));
            return f;
        });

        // ---- server/edit/ ----
        flow(r, "server/edit/fill-area-with-the-exact-result", 3000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            int[] before = {0};
            String[] id = {null};
            f.cmd("setblock " + (s.x + 4) + " " + (Y + 1) + " " + (s.z + 4) + " minecraft:chest 2 replace " + ITEMS)
                .untilOk("the chest", 200, cc -> blockAt(cc, s.x + 4, Y + 1, s.z + 4) == Blocks.CHEST ? null : "no chest yet")
                .step(cc -> {
                    before[0] = RemoteTasks.edited;
                    id[0] = select(cc, "lu-fill", s.x, Y, s.z, s.x + 9, Y + 3, s.z + 9);
                    ToolUtils.fillSelectionVolumes(GLASS, null);
                    return true;
                })
                .until("the server to finish the fill", 600, cc -> RemoteTasks.edited > before[0])
                .untilOk("all 400 blocks to be glass", 300, cc -> allOf(cc, Blocks.GLASS, s.x, Y, s.z, 10, 4, 10))
                .run(cc -> {
                    cc.check(blockAt(cc, s.x + 10, Y, s.z) == Blocks.AIR && blockAt(cc, s.x - 1, Y, s.z) == Blocks.AIR
                        && blockAt(cc, s.x, Y + 4, s.z) == Blocks.AIR && blockAt(cc, s.x, Y, s.z + 10) == Blocks.AIR, "blocks outside the area changed");
                    cc.check(cc.mc.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(s.x - 2, Y - 2, s.z - 2, s.x + 12, Y + 6, s.z + 12)).isEmpty(),
                        "the chest's items dropped");
                    unselect(id[0]);
                });
            return f;
        });
        flow(r, "server/edit/replace-only-the-chosen-block", 3000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            int[] before = {0};
            String[] id = {null};
            f.cmd("fill " + s.x + " " + Y + " " + s.z + " " + (s.x + 9) + " " + (Y + 3) + " " + (s.z + 9) + " minecraft:stone")
                .cmd("fill " + (s.x + 5) + " " + Y + " " + s.z + " " + (s.x + 9) + " " + (Y + 3) + " " + (s.z + 9) + " minecraft:dirt")
                .untilOk("the stone and dirt", 300, cc -> {
                    String stone = allOf(cc, Blocks.STONE, s.x, Y, s.z, 5, 4, 10);
                    return stone != null ? stone : allOf(cc, Blocks.DIRT, s.x + 5, Y, s.z, 5, 4, 10);
                })
                .step(cc -> {
                    before[0] = RemoteTasks.edited;
                    id[0] = select(cc, "lu-replace", s.x, Y, s.z, s.x + 9, Y + 3, s.z + 9);
                    ToolUtils.fillSelectionVolumes(GLASS, STONE);
                    return true;
                })
                .until("the server to finish the replace", 600, cc -> RemoteTasks.edited > before[0])
                .untilOk("stone replaced by glass, dirt left alone", 300, cc -> {
                    String glass = allOf(cc, Blocks.GLASS, s.x, Y, s.z, 5, 4, 10);
                    return glass != null ? glass : allOf(cc, Blocks.DIRT, s.x + 5, Y, s.z, 5, 4, 10);
                })
                .run(cc -> unselect(id[0]));
            return f;
        });
        flow(r, "server/edit/delete-area-clears-containers-and-entities", 3000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            int[] before = {0};
            scene(f, s.x, Y, s.z).untilOk("the scene", 300, cc -> sceneProblem(cc, s.x, Y, s.z))
                .run(cc -> {
                    before[0] = RemoteTasks.edited;
                    ToolUtils.deleteSelectionVolumes(area("lu del", s.x, Y, s.z, s.x + 7, Y + 3, s.z + 4), true);
                })
                .until("the server to finish the delete", 600, cc -> RemoteTasks.edited > before[0])
                .untilOk("the area to be empty", 300, cc -> airProblem(cc, s.x, Y, s.z, 8, 4, 5));
            return f;
        });

        // ---- server/move/ ----
        flow(r, "server/move/area-with-nbt-ends-up-at-the-new-spot", 5000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            int[] before = {0, 0};
            String[] id = {null};
            scene(f, s.x, Y, s.z).untilOk("the scene", 300, cc -> sceneProblem(cc, s.x, Y, s.z))
                .step(cc -> {
                    before[0] = RemoteTasks.saved;
                    before[1] = RemoteTasks.edited;
                    id[0] = select(cc, "lu-move", s.x, Y, s.z, s.x + 6, Y + 2, s.z + 3);
                    ToolUtils.moveCurrentlySelectedWorldRegionTo(lb(s.x + 12, Y, s.z));
                    return true;
                })
                .untilOk("the moved area", 1800, cc -> {
                    String old = airProblem(cc, s.x, Y, s.z, 7, 3, 4);
                    return old != null ? "old spot: " + old : sceneProblem(cc, s.x + 12, Y, s.z);
                });
            chestItems(f, s.x + 13, Y, s.z + 1);
            return f.run(cc -> {
                cc.check(RemoteTasks.saved == before[0] + 1, "the move did not save through the server");
                cc.check(RemoteTasks.edited == before[1] + 2, "the move did not delete and paste through the server (" + (RemoteTasks.edited - before[1]) + " edits)");
                unselect(id[0]);
            });
        });

        // ---- server/permission/ ----
        flow(r, "server/permission/edits-off-refuses-and-litematicas-own-paste-works", 4000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            RemoteClient.Remote[] raw = {null};
            int[] started = {0};
            f.cmd("litematicaupgraded edits off").until("the capabilities to say edits are off", 300, cc -> !ServerFeatures.edits)
                .step(cc -> {
                    raw[0] = rawFill(cc, s.x + 10, Y, s.z + 20);
                    started[0] = RemoteClient.started;
                    return true;
                })
                .until("the server's answer", 300, cc -> raw[0].ended())
                .run(cc -> {
                    refusedWith(cc, raw[0], ServerJobs.EDITS_DISABLED);
                    cc.check(blockAt(cc, s.x + 10, Y, s.z + 20) == Blocks.AIR, "the refused edit changed the world");
                });
            ownPaste(f, s, "server-perm-off");
            return f.run(cc -> cc.check(RemoteClient.started == started[0], "Litematica's own paste went to the server although edits are off"))
                .cmd("litematicaupgraded edits on").until("the capabilities to say edits are on", 300, cc -> ServerFeatures.edits);
        });
        flow(r, "server/permission/saving-off-refuses-and-litematicas-own-save-works", 4000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            RemoteClient.Remote[] raw = {null};
            Schematic[] out = {null};
            int[] before = {0};
            f.cmd("setblock " + (s.x + 1) + " " + Y + " " + (s.z + 1) + " minecraft:stone")
                .untilOk("the stone", 200, cc -> blockAt(cc, s.x + 1, Y, s.z + 1) == Blocks.STONE ? null : "no stone yet")
                .cmd("litematicaupgraded saving off").until("the capabilities to say saving is off", 300, cc -> !ServerFeatures.saving)
                .step(cc -> {
                    raw[0] = rawSave(cc, s.x, Y, s.z);
                    return true;
                })
                .until("the server's answer", 300, cc -> raw[0].ended())
                .run(cc -> refusedWith(cc, raw[0], ServerJobs.SAVING_DISABLED))
                .run(cc -> {
                    before[0] = RemoteTasks.saved;
                    TaskScheduler.getInstanceClient().scheduleTask(
                        new LocalCreateSchematicTask(area("lu own", s.x + 1, Y, s.z + 1, s.x + 1, Y, s.z + 1), new SchematicSaveSettings(), sch -> out[0] = sch), 1);
                })
                .until("Litematica's own save", 600, cc -> out[0] != null)
                .run(cc -> {
                    cc.check(RemoteTasks.saved == before[0], "the save went to the server although saving is off");
                    cc.check(only(out[0]).getBlockContainer().getBlockState(0, 0, 0).vanillaState().getBlock() == Blocks.STONE, "Litematica's own save lost the block");
                })
                .cmd("litematicaupgraded saving on").until("the capabilities to say saving is on", 300, cc -> ServerFeatures.saving);
            return f;
        });
        flow(r, "server/permission/survival-player-is-refused", 2000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            RemoteClient.Remote[] raw = {null};
            f.cmd("gamemode survival").until("survival mode", 200, cc -> !cc.mc.playerController.getCurrentGameType().isCreative())
                .step(cc -> {
                    raw[0] = rawFill(cc, s.x + 10, Y, s.z + 20);
                    return true;
                })
                .until("the server's answer", 300, cc -> raw[0].ended())
                .run(cc -> {
                    refusedWith(cc, raw[0], ServerJobs.CREATIVE);
                    cc.check(blockAt(cc, s.x + 10, Y, s.z + 20) == Blocks.AIR, "the refused edit changed the world");
                })
                .cmd("gamemode creative").until("creative mode", 200, cc -> cc.mc.playerController.getCurrentGameType().isCreative());
            return f;
        });
        flow(r, "server/permission/fallback-own-paste-works-without-the-server-part", 4000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            int[] before = {0, 0};
            f.run(cc -> {
                cc.check(!ServerFeatures.received && !ServerFeatures.edits && !ServerFeatures.saving, "the client believes the server has the server part");
                before[0] = RemoteClient.started;
                before[1] = RemoteTasks.edited;
            });
            ownPaste(f, s, "server-perm-plain");
            return f.run(cc -> cc.check(RemoteClient.started == before[0] && RemoteTasks.edited == before[1], "a job went to the server"));
        });

        // ---- server/limits/ ----
        flow(r, "server/limits/upload-cap-refuses-a-too-large-upload", 2500, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            RemoteClient.Remote[] raw = {null, null};
            f.cmd("litematicaupgraded edits limit 1").until("the capabilities to carry the cap", 300, cc -> ServerFeatures.uploadKiB == 1)
                .step(cc -> {
                    raw[0] = rawAnnounce(cc, 5);
                    return true;
                })
                .until("the server's answer", 300, cc -> raw[0].ended())
                .run(cc -> refusedWith(cc, raw[0], ServerJobs.TOO_LARGE))
                .cmd("litematicaupgraded edits limit 32768").until("the capabilities to carry the default cap", 300, cc -> ServerFeatures.uploadKiB == 32768)
                .step(cc -> {
                    raw[1] = rawAnnounce(cc, 2000);
                    return true;
                })
                .until("the server's answer", 300, cc -> raw[1].ended())
                .run(cc -> refusedWith(cc, raw[1], ServerJobs.TOO_LARGE));
            return f;
        });
        flow(r, "server/limits/one-running-edit-per-player-and-progress-is-shown", 4000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            RemoteClient.Remote[] old = {null}, first = {null}, second = {null};
            int[] before = {0, 0};
            f.cmd("litematicaupgraded edits rate 200").pause(5).step(cc -> {
                old[0] = RemoteTasks.lastRemote;
                before[0] = RemoteTasks.edited;
                before[1] = RemoteTasks.progressSeen;
                TaskScheduler.getInstanceClient().scheduleTask(bigFill(s), 1);
                return true;
            });
            accepted(f, old, first).step(cc -> {
                second[0] = rawFill(cc, s.x + 10, Y + 8, s.z + 10);
                return true;
            }).until("the server's answer to the second edit", 300, cc -> second[0].ended())
                .run(cc -> refusedWith(cc, second[0], ServerJobs.BUSY))
                .until("the server to finish the first edit", 1500, cc -> RemoteTasks.edited > before[0])
                .untilOk("the whole area to be glass", 300, cc -> allOf(cc, Blocks.GLASS, s.x, Y, s.z, AREA, AREA_HIGH, AREA))
                .run(cc -> {
                    cc.check(first[0].progressMessages >= 1 && RemoteTasks.progressSeen > before[1], "no progress of the server was shown");
                    boolean shown = false;
                    for (String notice : RemoteTasks.NOTICES) shown |= notice.contains(" of ") && notice.contains("blocks");
                    cc.check(shown, "no translated progress notice: " + RemoteTasks.NOTICES);
                    cc.check(blockAt(cc, s.x + 10, Y + 8, s.z + 10) == Blocks.AIR, "the refused second edit changed the world");
                })
                .cmd("litematicaupgraded edits rate 20000");
            return f;
        });
        flow(r, "server/limits/edit-can-be-cancelled", 4000, c -> {
            Site s = nextSite(c);
            Flow f = prep(new Flow(), s);
            RemoteClient.Remote[] old = {null}, first = {null};
            TaskFillArea[] task = {null};
            int[] counts = {0, 0, 0};
            f.cmd("litematicaupgraded edits rate 100").pause(5).step(cc -> {
                old[0] = RemoteTasks.lastRemote;
                task[0] = bigFill(s);
                TaskScheduler.getInstanceClient().scheduleTask(task[0], 1);
                return true;
            });
            accepted(f, old, first).until("the first progress", 400, cc -> first[0].progressMessages >= 1)
                .run(cc -> cc.check(TaskScheduler.getInstanceClient().removeTask(task[0]), "the task was not running"))
                .until("the server to confirm the cancel", 300, cc -> first[0].state == MessageEditStatus.CANCELLED)
                .pause(10)
                .run(cc -> {
                    counts[0] = count(cc, Blocks.GLASS, s.x, Y, s.z, AREA, AREA_HIGH, AREA);
                    cc.check(counts[0] > 0 && counts[0] < AREA * AREA * AREA_HIGH, counts[0] + " of " + AREA * AREA * AREA_HIGH + " blocks are glass: not a partly done edit");
                })
                .pause(20)
                .run(cc -> {
                    counts[1] = count(cc, Blocks.GLASS, s.x, Y, s.z, AREA, AREA_HIGH, AREA);
                    cc.check(counts[1] == counts[0], "the cancelled edit went on: " + counts[0] + " -> " + counts[1]);
                    counts[2] = RemoteTasks.edited;
                    TaskScheduler.getInstanceClient().scheduleTask(
                        new TaskFillArea(ImmutableList.of(new SelectionBox(lb(s.x, Y + 8, s.z), lb(s.x + 2, Y + 8, s.z + 2), "lu")), GLASS, null, false), 1);
                })
                .until("the next edit", 600, cc -> RemoteTasks.edited > counts[2])
                .untilOk("the next edit's result", 300, cc -> allOf(cc, Blocks.GLASS, s.x, Y + 8, s.z, 3, 1, 3))
                .cmd("litematicaupgraded edits rate 20000");
            return f;
        });
    }

    /** After the operator rights are gone (the last test of ServerTests takes them): the server refuses the edit and nothing changes. */
    static void registerLast(Runner.Registry r) {
        flow(r, "server/permission/non-op-is-refused-and-nothing-changes", 800, c -> {
            BlockPos at = c.mc.player.getPosition().add(4, 3, 4);
            IBlockState was = c.mc.world.getBlockState(at);
            RemoteClient.Remote[] raw = {null};
            return new Flow().step(cc -> {
                cc.check(cc.mc.playerController.getCurrentGameType().isCreative(), "the test player is not in creative mode");
                raw[0] = rawFill(cc, at.getX(), at.getY(), at.getZ());
                return true;
            }).until("the server's answer", 300, cc -> raw[0].ended())
                .run(cc -> {
                    refusedWith(cc, raw[0], ServerJobs.PERMISSION);
                    cc.check(cc.mc.world.getBlockState(at) == was, "the refused edit changed the world");
                });
        });
    }
}
