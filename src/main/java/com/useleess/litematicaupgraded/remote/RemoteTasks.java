// The client side of server-side schematics: Litematica's own save / paste / fill / replace / delete tasks hand their work to the server part.
package com.useleess.litematicaupgraded.remote;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.scheduler.task.LocalCreateSchematicTask;
import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskDeleteArea;
import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskFillArea;
import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskPasteSchematicPerChunkCommand;
import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskProcessChunkBase;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicSaveSettings;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.data.EntityData;
import com.useleess.litematicaupgraded.litematica.selection.SelectionBox;
import com.useleess.litematicaupgraded.litematica.util.value.ReplaceBehavior;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.litematica.world.WorldSchematic;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.converter.DataConverterNbt;
import com.useleess.litematicaupgraded.malilib.util.data.tag.util.DataTypeUtils;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.WorldWrap;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.ChunkPos;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;
import com.useleess.litematicaupgraded.malilib.util.position.PositionUtils;
import com.useleess.litematicaupgraded.malilib.util.position.Vec3d;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.mixin.IMixinLocalCreateSchematicTask;
import com.useleess.litematicaupgraded.mixin.IMixinTaskBase;
import com.useleess.litematicaupgraded.mixin.IMixinTaskFillArea;
import com.useleess.litematicaupgraded.mixin.IMixinTaskPasteSchematicPerChunkBase;
import com.useleess.litematicaupgraded.mixin.IMixinTaskPasteSchematicPerChunkCommand;
import com.useleess.litematicaupgraded.mixin.IMixinTaskProcessChunkBase;
import com.useleess.litematicaupgraded.server.RemoteBlob;
import com.useleess.litematicaupgraded.server.RemoteClient;
import com.useleess.litematicaupgraded.server.ServerFeatures;
import com.useleess.litematicaupgraded.server.ServerJobs;
import com.useleess.litematicaupgraded.server.Slices;
import com.useleess.litematicaupgraded.server.StateStrings;
import com.useleess.litematicaupgraded.server.net.MessageEditStatus;
import com.useleess.litematicaupgraded.server.net.MessageSaveRequest;
import com.useleess.litematicaupgraded.server.net.Net;

/**
 * Called by the mixins on Litematica's tasks. When the server says it saves / edits (and this is not a single player world), a
 * task sends its job to the server on its first run and then only waits: a save gets the area back with full NBT, an edit is
 * done by the server on its ticks. Otherwise the task runs exactly as it always did. The client shows a translated status
 * through Litematica's message system.
 */
public final class RemoteTasks {
    private enum Kind { SAVE, FILL, DELETE, PASTE }

    /** A task that went to the server. */
    private static final class Active {
        final Kind kind;
        final RemoteClient.Remote remote;
        boolean done;
        int shownProgress;
        long lastProgressNanos;

        Active(Kind kind, RemoteClient.Remote remote) {
            this.kind = kind;
            this.remote = remote;
        }
    }

    /** Marks a task that runs the way Litematica always did. */
    private static final Active LOCAL = new Active(Kind.SAVE, null);
    private static final Map<Object, Active> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());

    /** The translated notices shown to the player, newest last (for tests). */
    public static final List<String> NOTICES = new CopyOnWriteArrayList<>();
    /** How many tasks of each kind the server finished, and how many were refused or failed (for tests). */
    public static volatile int saved, edited, refused;
    /** How many progress updates of the server were shown, and the job started last (for tests). */
    public static volatile int progressSeen;
    public static volatile RemoteClient.Remote lastRemote;

    private RemoteTasks() {}

    /** Whether the server part does saves (edit = false) or edits (edit = true) for this connection. */
    public static boolean serverDoes(boolean edit) {
        return !GameWrap.isSinglePlayer() && ServerFeatures.received && (edit ? ServerFeatures.edits : ServerFeatures.saving);
    }

    public static boolean isRemote(Object task) {
        Active active = ACTIVE.get(task);
        return active != null && active != LOCAL;
    }

    // ---- hooks ----

    /** A save or fill / delete task's run: null = run as usual, else what execute() returns. */
    public static Boolean execute(TaskProcessChunkBase task) {
        boolean save = task instanceof LocalCreateSchematicTask;
        if (!save && !(task instanceof TaskFillArea)) return null;
        Active active = ACTIVE.get(task);
        if (active == null) {
            if (!serverDoes(!save) || !((IMixinTaskProcessChunkBase) task).litematicaUpgradedIsClientWorld()) {
                ACTIVE.put(task, LOCAL);
                return null;
            }
            active = save ? startSave((LocalCreateSchematicTask) task) : startFill((TaskFillArea) task);
            ACTIVE.put(task, active);
        }
        if (active == LOCAL) return null;
        return poll(task, active);
    }

    /** A command paste task's run. */
    public static Boolean executePaste(TaskPasteSchematicPerChunkCommand task) {
        Active active = ACTIVE.get(task);
        if (active == null) {
            if (!serverDoes(true)) {
                ACTIVE.put(task, LOCAL);
                return null;
            }
            if (!schematicReady((IMixinTaskPasteSchematicPerChunkBase) task)) return Boolean.FALSE;
            active = startPaste(task);
            ACTIVE.put(task, active);
        }
        if (active == LOCAL) return null;
        return poll(task, active);
    }

    /** The task is being stopped: a job the server still works on is cancelled. */
    public static void stopped(Object task) {
        Active active = ACTIVE.get(task);
        if (active == null || active == LOCAL || active.done || active.remote.ended()) return;
        active.done = true;
        RemoteClient.cancel(active.remote);
        notice(false, "litematica_upgraded.message.server.cancelled", jobName(active.kind));
    }

    // ---- starting ----

    private static Active startSave(LocalCreateSchematicTask task) {
        IMixinLocalCreateSchematicTask access = (IMixinLocalCreateSchematicTask) task;
        SchematicSaveSettings settings = access.litematicaUpgradedSettings();
        int flags = (settings.saveBlocks.getBooleanValue() ? MessageSaveRequest.BLOCKS : 0)
            | (settings.saveBlockEntities.getBooleanValue() ? MessageSaveRequest.TILES : 0)
            | (settings.saveEntities.getBooleanValue() ? MessageSaveRequest.ENTITIES : 0);
        List<MessageSaveRequest.Box> boxes = new ArrayList<>();
        for (SelectionBox box : access.litematicaUpgradedSubRegions().values()) {
            BlockPos a = box.getCorner1(), b = box.getCorner2();
            boxes.add(new MessageSaveRequest.Box(box.getName(), a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()));
        }
        RemoteClient.Remote remote = RemoteClient.begin();
        lastRemote = remote;
        Active active = new Active(Kind.SAVE, remote);
        notice(false, "litematica_upgraded.message.server.sent", jobName(Kind.SAVE));
        Net.CHANNEL.sendToServer(new MessageSaveRequest(remote.id, Minecraft.getMinecraft().player.dimension, flags, boxes));
        return active;
    }

    private static Active startFill(TaskFillArea task) {
        IMixinTaskFillArea fill = (IMixinTaskFillArea) task;
        NBTTagList boxes = new NBTTagList();
        for (IntBoundingBox box : ((IMixinTaskProcessChunkBase) task).litematicaUpgradedBoxesInChunks().values()) {
            boxes.appendTag(new NBTTagIntArray(new int[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ}));
        }
        NBTTagCompound blob = new NBTTagCompound();
        blob.setString("op", RemoteBlob.OP_FILL);
        blob.setTag("boxes", boxes);
        blob.setString("fill", StateStrings.toString(fill.litematicaUpgradedFillState()));
        IBlockState replace = fill.litematicaUpgradedReplaceState();
        blob.setString("replace", replace == null ? "" : StateStrings.toString(replace));
        blob.setBoolean("entities", fill.litematicaUpgradedRemoveEntities());
        task.disableCompletionMessage();
        return startUpload(task instanceof TaskDeleteArea ? Kind.DELETE : Kind.FILL, blob);
    }

    private static Active startPaste(TaskPasteSchematicPerChunkCommand task) {
        IMixinTaskPasteSchematicPerChunkBase paste = (IMixinTaskPasteSchematicPerChunkBase) task;
        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
        List<RemoteBlob.Region> regions = new ArrayList<>();
        java.util.Set<String> entities = new java.util.HashSet<>();
        for (IntBoundingBox box : paste.litematicaUpgradedBoxesInChunks().values()) regions.add(readSchematicBox(world, box, entities));
        ReplaceBehavior replace = paste.litematicaUpgradedReplace();
        NBTTagCompound blob = new NBTTagCompound();
        blob.setString("op", RemoteBlob.OP_PASTE);
        blob.setTag("regions", RemoteBlob.regionList(regions));
        blob.setInteger("replace", replace == ReplaceBehavior.NONE ? 1 : replace == ReplaceBehavior.WITH_NON_AIR ? 2 : 0);
        blob.setBoolean("changed", paste.litematicaUpgradedChangedBlockOnly());
        task.disableCompletionMessage();
        return startUpload(Kind.PASTE, blob);
    }

    /** The blocks, block entities and entities of the schematic world inside a box. */
    private static RemoteBlob.Region readSchematicBox(WorldSchematic world, IntBoundingBox box, java.util.Set<String> seen) {
        RemoteBlob.Region region = new RemoteBlob.Region("", box.minX, box.minY, box.minZ,
            box.maxX - box.minX + 1, box.maxY - box.minY + 1, box.maxZ - box.minZ + 1);
        net.minecraft.util.math.BlockPos.MutableBlockPos pos = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        for (int y = box.minY; y <= box.maxY; y++) {
            for (int z = box.minZ; z <= box.maxZ; z++) {
                for (int x = box.minX; x <= box.maxX; x++) {
                    pos.setPos(x, y, z);
                    IBlockState state = world.getBlockState(pos);
                    region.set(x, y, z, StateStrings.toString(state));
                    if (state.getBlock().hasTileEntity(state)) {
                        TileEntity te = world.getTileEntity(pos);
                        if (te != null) region.tiles.add(te.writeToNBT(new NBTTagCompound()));
                    }
                }
            }
        }
        AxisAlignedBB bb = new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY + 1, box.maxZ + 1);
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(null, bb)) {
            // an entity on the border of two boxes is found in both: pasted once
            if (entity instanceof EntityPlayer) continue;
            NBTTagCompound tag = new NBTTagCompound();
            if (!entity.writeToNBTOptional(tag)) continue;
            // the same kind at the same spot is the same entity (found in two boxes, or placed twice in the schematic world)
            String key = tag.getString("id") + String.format(java.util.Locale.ROOT, "@%.3f,%.3f,%.3f", entity.posX, entity.posY, entity.posZ);
            if (seen.add(key)) region.entities.add(tag);
        }
        return region;
    }

    /** The schematic world has all chunks of the paste and no rebuild of them is pending. */
    private static boolean schematicReady(IMixinTaskPasteSchematicPerChunkBase paste) {
        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
        if (world == null) return false;
        for (ChunkPos pos : paste.litematicaUpgradedBoxesInChunks().keySet()) {
            if (!WorldWrap.isClientChunkLoaded(pos.x, pos.z, world) || DataManager.getSchematicPlacementManager().hasPendingRebuildForChunk(pos.x, pos.z)) return false;
        }
        return true;
    }

    private static Active startUpload(Kind kind, NBTTagCompound blob) {
        byte[] data;
        try {
            data = RemoteBlob.gzip(blob);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        RemoteClient.Remote remote = RemoteClient.begin();
        lastRemote = remote;
        Active active = new Active(kind, remote);
        int count = Slices.count(data.length, Slices.SLICE_BYTES);
        int capKiB = ServerFeatures.uploadKiB;
        if (capKiB > 0 && Slices.exceedsCap(count, Slices.SLICE_BYTES, (long) capKiB * 1024)) {
            // the server would refuse it: nothing is sent
            MessageEditStatus status = MessageEditStatus.refused(remote.id, ServerJobs.TOO_LARGE);
            RemoteClient.onStatus(status);
            return active;
        }
        notice(false, "litematica_upgraded.message.server.sent", jobName(kind));
        RemoteClient.upload(remote, Minecraft.getMinecraft().player.dimension, data);
        return active;
    }

    // ---- running ----

    private static Boolean poll(Object task, Active active) {
        RemoteClient.Remote remote = active.remote;
        IMixinTaskBase base = (IMixinTaskBase) task;
        int state = remote.state;
        if (state == MessageEditStatus.REFUSED || state == MessageEditStatus.FAILED || state == MessageEditStatus.CANCELLED) {
            active.done = true;
            refused++;
            RemoteClient.forget(remote);
            notice(true, "litematica_upgraded.message.server.refused", StringUtils.translate(remote.key.isEmpty() ? ServerJobs.FAILED_KEY : remote.key));
            base.litematicaUpgradedSetFinished(false);
            return Boolean.TRUE;
        }
        boolean finished = active.kind == Kind.SAVE ? remote.downloaded() : state == MessageEditStatus.FINISHED;
        // progress that arrived together with the finish is still shown (it was skipped when both came in one tick)
        showProgress(base, active);
        if (finished) {
            active.done = true;
            if (active.kind == Kind.SAVE) {
                try {
                    applySave((LocalCreateSchematicTask) task, remote);
                } catch (IOException | RuntimeException e) {
                    refused++;
                    RemoteClient.forget(remote);
                    notice(true, "litematica_upgraded.message.server.refused", StringUtils.translate(ServerJobs.INVALID));
                    base.litematicaUpgradedSetFinished(false);
                    return Boolean.TRUE;
                }
                saved++;
                notice(false, "litematica_upgraded.message.server.saved", remote.slices.count());
            } else {
                edited++;
                if (task instanceof TaskPasteSchematicPerChunkCommand) {
                    ((IMixinTaskPasteSchematicPerChunkCommand) task).litematicaUpgradedSetSentCommandsTotal((int) remote.affected);
                }
                notice(false, "litematica_upgraded.message.server.finished", jobName(active.kind), remote.affected);
            }
            RemoteClient.forget(remote);
            base.litematicaUpgradedSetFinished(true);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private static void showProgress(IMixinTaskBase base, Active active) {
        RemoteClient.Remote remote = active.remote;
        if (remote.progressMessages == active.shownProgress) return;
        active.shownProgress = remote.progressMessages;
        progressSeen++;
        String text = StringUtils.translate("litematica_upgraded.message.server.progress", jobName(active.kind), remote.completed, remote.total);
        base.litematicaUpgradedSetInfoHudLines(Collections.singletonList(text));
        long now = System.nanoTime();
        if (now - active.lastProgressNanos > 1_000_000_000L) {
            active.lastProgressNanos = now;
            notice(false, "litematica_upgraded.message.server.progress", jobName(active.kind), remote.completed, remote.total);
        }
    }

    private static String jobName(Kind kind) {
        switch (kind) {
            case SAVE: return StringUtils.translate("litematica_upgraded.message.server.job.save");
            case PASTE: return StringUtils.translate("litematica_upgraded.message.server.job.paste");
            case DELETE: return StringUtils.translate("litematica_upgraded.message.server.job.delete");
            default: return StringUtils.translate("litematica_upgraded.message.server.job.fill");
        }
    }

    private static void notice(boolean error, String key, Object... args) {
        NOTICES.add(StringUtils.translate(key, args));
        if (NOTICES.size() > 200) NOTICES.remove(0);
        if (error) MessageDispatcher.error(key, args);
        else MessageDispatcher.generic(key, args);
    }

    // ---- the data of a server-side save goes where the local save would have put it ----

    private static void applySave(LocalCreateSchematicTask task, RemoteClient.Remote remote) throws IOException {
        IMixinLocalCreateSchematicTask access = (IMixinLocalCreateSchematicTask) task;
        SchematicSaveSettings settings = access.litematicaUpgradedSettings();
        boolean saveBlocks = settings.saveBlocks.getBooleanValue();
        boolean saveBlockEntities = settings.saveBlockEntities.getBooleanValue();
        boolean saveEntities = settings.saveEntities.getBooleanValue();
        Collection<SelectionBox> boxes = access.litematicaUpgradedSubRegions().values();
        Map<String, RemoteBlob.Region> regions = new java.util.HashMap<>();
        for (RemoteBlob.Region region : RemoteBlob.regions(remote.result())) regions.put(region.name, region);
        BlockPos.MutBlockPos pos = new BlockPos.MutBlockPos();
        long blocks = access.litematicaUpgradedTotalBlocks();
        long blockEntities = access.litematicaUpgradedTotalBlockEntities();
        int entityCount = access.litematicaUpgradedTotalEntities();

        for (SelectionBox box : boxes) {
            String name = box.getName();
            RemoteBlob.Region region = regions.get(name);
            BlockPos minCorner = PositionUtils.getMinCorner(box.getCorner1(), box.getCorner2());
            BlockContainer container = access.litematicaUpgradedBlockContainers().computeIfAbsent(name, key -> access.litematicaUpgradedCreateBlockContainer(box));
            Map<BlockPos, CompoundData> blockEntityMap = access.litematicaUpgradedBlockEntityMaps().computeIfAbsent(name, key -> new java.util.HashMap<>());
            if (region == null) continue;

            List<BlockState> palette = new ArrayList<>();
            for (String state : region.palette) {
                IBlockState parsed = StateStrings.parse(state);
                palette.add(BlockState.of(parsed != null ? parsed : net.minecraft.init.Blocks.AIR.getDefaultState()));
            }
            boolean[] kept = new boolean[region.indices.length];
            for (int i = 0; i < region.indices.length; i++) {
                int index = region.indices[i];
                if (index < 0) continue;
                BlockState state = palette.get(index);
                int x = region.x + i % region.sx, z = region.z + (i / region.sx) % region.sz, y = region.y + i / (region.sx * region.sz);
                pos.set(x, y, z);
                if (!access.litematicaUpgradedShouldSaveBlock(state, pos)) continue;
                kept[i] = true;
                if (saveBlocks) {
                    container.setBlockState(x - minCorner.getX(), y - minCorner.getY(), z - minCorner.getZ(), state);
                    blocks++;
                }
            }
            if (saveBlockEntities) {
                for (NBTTagCompound tile : region.tiles) {
                    int x = tile.getInteger("x"), y = tile.getInteger("y"), z = tile.getInteger("z");
                    if (x < region.x || x >= region.x + region.sx || y < region.y || y >= region.y + region.sy || z < region.z || z >= region.z + region.sz) continue;
                    if (!kept[region.offset(x, y, z)]) continue;
                    CompoundData data = DataConverterNbt.fromVanillaCompound(tile);
                    DataTypeUtils.removeBlockPosFromTag(data);
                    blockEntityMap.put(new BlockPos(x - minCorner.getX(), y - minCorner.getY(), z - minCorner.getZ()), data);
                    blockEntities++;
                }
            }
            if (saveEntities) {
                List<EntityData> list = access.litematicaUpgradedEntityLists().computeIfAbsent(name, key -> new ArrayList<>());
                BlockPos origin = box.getCorner1();
                for (NBTTagCompound tag : region.entities) {
                    NBTTagList position = tag.getTagList("Pos", 6);
                    if (position.tagCount() != 3) continue;
                    Vec3d relative = new Vec3d(position.getDoubleAt(0) - origin.getX(), position.getDoubleAt(1) - origin.getY(), position.getDoubleAt(2) - origin.getZ());
                    CompoundData data = DataConverterNbt.fromVanillaCompound(tag);
                    DataTypeUtils.writeVec3dToListTag(data, relative);
                    list.add(new EntityData(relative, data));
                    entityCount++;
                }
            }
        }
        access.litematicaUpgradedSetTotalBlocks(blocks);
        access.litematicaUpgradedSetTotalBlockEntities(blockEntities);
        access.litematicaUpgradedSetTotalEntities(entityCount);
    }
}
