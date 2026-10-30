// The server side of server-side schematics: saving areas and running edits, after Schematica Upgraded's RemoteEdits / WorldEditQueue / DownloadHandler (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;

import com.useleess.litematicaupgraded.LitematicaUpgraded;
import com.useleess.litematicaupgraded.server.net.MessageEditStatus;
import com.useleess.litematicaupgraded.server.net.MessageEditUpload;
import com.useleess.litematicaupgraded.server.net.MessageSaveData;
import com.useleess.litematicaupgraded.server.net.MessageSaveRequest;
import com.useleess.litematicaupgraded.server.net.Net;

/**
 * Saves read the world on the server (full NBT, also chunks the player has not loaded) and go back in slices; edits
 * (paste, fill, replace, delete) arrive in slices, are checked and run on server ticks, spread over many ticks. A player has
 * at most one save or edit at a time; it can be cancelled; the client is told the progress.
 */
public final class ServerJobs {
    public static final ServerJobs INSTANCE = new ServerJobs();

    public static final String EDITS_DISABLED = "litematica_upgraded.message.server.refused.edits_disabled";
    public static final String SAVING_DISABLED = "litematica_upgraded.message.server.refused.saving_disabled";
    public static final String CREATIVE = "litematica_upgraded.message.server.refused.creative";
    public static final String PERMISSION = "litematica_upgraded.message.server.refused.permission";
    public static final String BUSY = "litematica_upgraded.message.server.refused.busy";
    public static final String TOO_LARGE = "litematica_upgraded.message.server.refused.too_large";
    public static final String INVALID = "litematica_upgraded.message.server.refused.invalid";
    public static final String FAILED_KEY = "litematica_upgraded.message.server.failed";

    /** The largest area one save may read. */
    public static final long MAX_SAVE_VOLUME = 16_000_000L;
    private static final long TICK_BUDGET_NANOS = 8_000_000L;
    private static final long IDLE_NANOS = 60_000_000_000L;
    private static final int SLICES_PER_TICK = 4, PROGRESS_TICKS = 5;
    /** The block flags of a change: send it to the clients, but no neighbor updates. */
    private static final int FLAGS = 2 | 16;

    /** A running job. */
    abstract static class Job {
        final UUID owner;
        final long id;
        final int dimension;
        int completed, total;
        long affected;

        Job(UUID owner, long id, int dimension) {
            this.owner = owner;
            this.id = id;
            this.dimension = dimension;
        }

        abstract boolean isEdit();

        /** Works until the deadline; true when the job is done. */
        abstract boolean step(WorldServer world, long deadline) throws Exception;
    }

    private static final class Upload {
        final long id;
        final int dimension;
        final Slices.Assembler slices;
        long touched = System.nanoTime();

        Upload(long id, int dimension, Slices.Assembler slices) {
            this.id = id;
            this.dimension = dimension;
            this.slices = slices;
        }
    }

    private final Map<UUID, Job> jobs = new HashMap<>();
    private final Map<UUID, Upload> uploads = new HashMap<>();
    private int tick;

    private ServerJobs() {}

    private static void send(EntityPlayerMP player, MessageEditStatus status) {
        Net.CHANNEL.sendTo(status, player);
    }

    private static MessageEditStatus status(Job job, int state) {
        MessageEditStatus status = new MessageEditStatus(job.id, state);
        status.completed = job.completed;
        status.total = job.total;
        status.affected = job.affected;
        return status;
    }

    /** Why the player may not run server-side edits right now (a translation key), or null. */
    public static String editRefusal(EntityPlayerMP player) {
        if (!ServerConfig.edits) return EDITS_DISABLED;
        if (!player.capabilities.isCreativeMode) return CREATIVE;
        if (ServerConfig.editLevel > 0 && !player.canUseCommand(ServerConfig.editLevel, "setblock")) return PERMISSION;
        return null;
    }

    // ---- messages (server thread) ----

    public void upload(EntityPlayerMP player, MessageEditUpload m) {
        UUID owner = player.getUniqueID();
        Upload upload = uploads.get(owner);
        if (m.index == 0) {
            String refusal = editRefusal(player);
            if (refusal == null && m.dimension != player.dimension) refusal = INVALID;
            if (refusal == null && Slices.exceedsCap(m.count, Slices.SLICE_BYTES, ServerConfig.maxUploadBytes())) refusal = TOO_LARGE;
            if (refusal == null && (jobs.containsKey(owner) || upload != null)) refusal = BUSY;
            if (refusal != null) {
                send(player, MessageEditStatus.refused(m.id, refusal));
                return;
            }
            upload = new Upload(m.id, m.dimension, new Slices.Assembler(m.count, ServerConfig.maxUploadBytes()));
            uploads.put(owner, upload);
        }
        // the rest of a refused or cancelled edit
        if (upload == null || upload.id != m.id) return;
        String error = m.dimension != upload.dimension || m.count != upload.slices.count() ? Slices.Assembler.INVALID : upload.slices.put(m.index, m.data);
        if (error != null) {
            uploads.remove(owner);
            send(player, MessageEditStatus.refused(m.id, Slices.Assembler.TOO_LARGE.equals(error) ? TOO_LARGE : INVALID));
            return;
        }
        upload.touched = System.nanoTime();
        if (!upload.slices.complete()) return;
        uploads.remove(owner);
        Job job;
        try {
            job = buildEdit(owner, upload, RemoteBlob.read(upload.slices.join()));
        } catch (IOException | RuntimeException e) {
            LogManager.getLogger(LitematicaUpgraded.MOD_ID).warn("Rejected an invalid edit from {}", player.getName(), e);
            send(player, MessageEditStatus.refused(m.id, INVALID));
            return;
        }
        jobs.put(owner, job);
        send(player, status(job, MessageEditStatus.ACCEPTED));
    }

    public void save(EntityPlayerMP player, MessageSaveRequest m) {
        UUID owner = player.getUniqueID();
        String refusal = null;
        if (!ServerConfig.saving) refusal = SAVING_DISABLED;
        else if (m.dimension != player.dimension || m.boxes.isEmpty()) refusal = INVALID;
        else if (jobs.containsKey(owner) || uploads.containsKey(owner)) refusal = BUSY;
        long volume = 0;
        if (refusal == null) {
            for (MessageSaveRequest.Box box : m.boxes) {
                int[] c = box.corners;
                volume += (long) (Math.abs(c[3] - c[0]) + 1) * (Math.abs(c[4] - c[1]) + 1) * (Math.abs(c[5] - c[2]) + 1);
            }
            if (volume > MAX_SAVE_VOLUME) refusal = TOO_LARGE;
        }
        if (refusal != null) {
            send(player, MessageEditStatus.refused(m.id, refusal));
            return;
        }
        Job job = new SaveJob(owner, m.id, m.dimension, m);
        jobs.put(owner, job);
        send(player, status(job, MessageEditStatus.ACCEPTED));
    }

    public void cancel(EntityPlayerMP player, long id) {
        UUID owner = player.getUniqueID();
        Job job = jobs.get(owner);
        Upload upload = uploads.get(owner);
        if (job != null && job.id == id) {
            jobs.remove(owner);
            send(player, status(job, MessageEditStatus.CANCELLED));
        } else if (upload != null && upload.id == id) {
            uploads.remove(owner);
            send(player, new MessageEditStatus(id, MessageEditStatus.CANCELLED));
        }
    }

    /** The player left: nothing is kept. */
    public void forget(EntityPlayer player) {
        jobs.remove(player.getUniqueID());
        uploads.remove(player.getUniqueID());
    }

    /** How many jobs run (for the status command). */
    public int running() {
        return jobs.size();
    }

    // ---- building an edit ----

    private static Job buildEdit(UUID owner, Upload upload, NBTTagCompound blob) {
        String op = blob.getString("op");
        if (RemoteBlob.OP_FILL.equals(op)) {
            IBlockState fill = StateStrings.parse(blob.getString("fill"));
            String replaceText = blob.getString("replace");
            IBlockState replace = replaceText.isEmpty() ? null : StateStrings.parse(replaceText);
            if (fill == null || (!replaceText.isEmpty() && replace == null)) throw new IllegalArgumentException("unknown block state");
            List<int[]> boxes = new ArrayList<>();
            NBTTagList list = blob.getTagList("boxes", 11);
            for (int i = 0; i < list.tagCount(); i++) {
                int[] box = list.getIntArrayAt(i);
                if (box.length != 6) throw new IllegalArgumentException("bad box");
                boxes.add(box);
            }
            return new FillJob(owner, upload.id, upload.dimension, boxes, fill, replace, blob.getBoolean("entities"));
        }
        if (RemoteBlob.OP_PASTE.equals(op)) {
            return new PasteJob(owner, upload.id, upload.dimension, RemoteBlob.regions(blob), blob.getInteger("replace"), blob.getBoolean("changed"));
        }
        throw new IllegalArgumentException("unknown operation " + op);
    }

    // ---- the tick ----

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (jobs.isEmpty() && uploads.isEmpty())) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        tick++;
        long now = System.nanoTime();
        for (Iterator<Map.Entry<UUID, Upload>> it = uploads.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Upload> entry = it.next();
            if (now - entry.getValue().touched < IDLE_NANOS) continue;
            it.remove();
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (player != null) send(player, MessageEditStatus.refused(entry.getValue().id, INVALID));
        }
        int running = Math.max(1, jobs.size());
        for (Iterator<Map.Entry<UUID, Job>> it = jobs.entrySet().iterator(); it.hasNext(); ) {
            Job job = it.next().getValue();
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(job.owner);
            if (player == null) {
                it.remove();
                continue;
            }
            String refusal = job.isEdit() ? editRefusal(player) : ServerConfig.saving ? null : SAVING_DISABLED;
            if (refusal == null && player.dimension != job.dimension) refusal = INVALID;
            if (refusal != null) {
                it.remove();
                send(player, MessageEditStatus.refused(job.id, refusal));
                continue;
            }
            try {
                if (job.step(server.getWorld(job.dimension), System.nanoTime() + TICK_BUDGET_NANOS / running)) {
                    it.remove();
                    send(player, status(job, MessageEditStatus.FINISHED));
                } else if (tick % PROGRESS_TICKS == 0) {
                    send(player, status(job, MessageEditStatus.PROGRESS));
                }
            } catch (Exception e) {
                LogManager.getLogger(LitematicaUpgraded.MOD_ID).error("A server-side schematic job of {} failed", player.getName(), e);
                it.remove();
                MessageEditStatus failed = status(job, MessageEditStatus.FAILED);
                failed.key = FAILED_KEY;
                send(player, failed);
            }
        }
    }

    // ---- helpers ----

    private static boolean timeUp(long deadline, int counter) {
        return (counter & 127) == 0 && System.nanoTime() >= deadline;
    }

    /** Clears a container before its block is replaced, so no items drop. */
    private static void clearContainer(WorldServer world, BlockPos pos, IBlockState old) {
        if (!old.getBlock().hasTileEntity(old)) return;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IInventory) {
            ((IInventory) te).clear();
            world.setBlockState(pos, Blocks.BARRIER.getDefaultState(), FLAGS);
        }
    }

    private static void killEntities(WorldServer world, AxisAlignedBB box) {
        for (Entity entity : world.getEntitiesInAABBexcluding(null, box, e -> !(e instanceof EntityPlayer))) entity.setDead();
    }

    // ---- fill / replace / delete ----

    private static final class FillJob extends Job {
        private final List<int[]> boxes;
        private final IBlockState fill, replace;
        private final boolean entities;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int box;
        private long at;

        FillJob(UUID owner, long id, int dimension, List<int[]> boxes, IBlockState fill, IBlockState replace, boolean entities) {
            super(owner, id, dimension);
            this.boxes = new ArrayList<>();
            long volume = 0;
            for (int[] b : boxes) {
                int[] n = {Math.min(b[0], b[3]), Math.min(b[1], b[4]), Math.min(b[2], b[5]), Math.max(b[0], b[3]), Math.max(b[1], b[4]), Math.max(b[2], b[5])};
                this.boxes.add(n);
                volume += (long) (n[3] - n[0] + 1) * (n[4] - n[1] + 1) * (n[5] - n[2] + 1);
            }
            this.total = (int) Math.min(Integer.MAX_VALUE, volume);
            this.fill = fill;
            this.replace = replace;
            this.entities = entities;
        }

        @Override boolean isEdit() { return true; }

        @Override boolean step(WorldServer world, long deadline) {
            int budget = ServerConfig.blocksPerTick;
            int counter = 0;
            while (box < boxes.size() && budget > 0) {
                int[] b = boxes.get(box);
                int sx = b[3] - b[0] + 1, sy = b[4] - b[1] + 1, sz = b[5] - b[2] + 1;
                long volume = (long) sx * sy * sz;
                if (at == 0 && entities) killEntities(world, new AxisAlignedBB(b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1));
                while (at < volume && budget > 0) {
                    long column = at / sy;
                    int y = b[4] - (int) (at % sy);
                    pos.setPos(b[0] + (int) (column % sx), y, b[2] + (int) (column / sx));
                    at++;
                    completed++;
                    budget--;
                    if (y >= 0 && y < 256) change(world);
                    if (timeUp(deadline, ++counter)) return false;
                }
                if (at >= volume) {
                    box++;
                    at = 0;
                }
            }
            return box >= boxes.size();
        }

        private void change(WorldServer world) {
            IBlockState old = world.getBlockState(pos);
            if (replace == null ? old == fill : old != replace) return;
            clearContainer(world, pos, old);
            world.setBlockState(pos, fill, FLAGS);
            affected++;
        }
    }

    // ---- paste ----

    private static final class PasteJob extends Job {
        private final List<RemoteBlob.Region> regions;
        private final int replace;
        private final boolean changedOnly;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final List<IBlockState> palette = new ArrayList<>();
        private int region, phase;
        private int at;

        PasteJob(UUID owner, long id, int dimension, List<RemoteBlob.Region> regions, int replace, boolean changedOnly) {
            super(owner, id, dimension);
            this.regions = regions;
            this.replace = replace;
            this.changedOnly = changedOnly;
            long volume = 0;
            for (RemoteBlob.Region r : regions) {
                volume += r.volume();
                for (String state : r.palette) if (StateStrings.parse(state) == null) throw new IllegalArgumentException("unknown block state " + state);
            }
            this.total = (int) Math.min(Integer.MAX_VALUE, volume);
        }

        @Override boolean isEdit() { return true; }

        @Override boolean step(WorldServer world, long deadline) {
            int budget = ServerConfig.blocksPerTick;
            int counter = 0;
            while (region < regions.size() && budget > 0) {
                RemoteBlob.Region r = regions.get(region);
                if (phase == 0) {
                    if (at == 0) {
                        palette.clear();
                        for (String state : r.palette) palette.add(StateStrings.parse(state));
                    }
                    while (at < r.indices.length && budget > 0) {
                        int index = r.indices[at];
                        int i = at++;
                        completed++;
                        budget--;
                        if (index >= 0) {
                            pos.setPos(r.x + i % r.sx, r.y + i / (r.sx * r.sz), r.z + (i / r.sx) % r.sz);
                            if (pos.getY() >= 0 && pos.getY() < 256) place(world, palette.get(index));
                        }
                        if (timeUp(deadline, ++counter)) return false;
                    }
                    if (at >= r.indices.length) phase = 1;
                } else {
                    finishRegion(world, r);
                    region++;
                    phase = 0;
                    at = 0;
                }
            }
            return region >= regions.size();
        }

        /** Litematica's own paste rules, decided here on the real world: 0 = replace all, 1 = only into air, 2 = not with air. */
        private void place(WorldServer world, IBlockState target) {
            IBlockState old = world.getBlockState(pos);
            boolean air = target.getBlock() == Blocks.AIR;
            if (air && old.getBlock() == Blocks.AIR) return;
            if (changedOnly && old == target) return;
            if (replace == 1 && old.getBlock() != Blocks.AIR) return;
            if (replace == 2 && air) return;
            clearContainer(world, pos, old);
            world.setBlockState(pos, target, FLAGS);
            affected++;
        }

        private void finishRegion(WorldServer world, RemoteBlob.Region r) {
            for (NBTTagCompound tile : r.tiles) {
                pos.setPos(tile.getInteger("x"), tile.getInteger("y"), tile.getInteger("z"));
                if (pos.getX() < r.x || pos.getX() >= r.x + r.sx || pos.getY() < r.y || pos.getY() >= r.y + r.sy || pos.getZ() < r.z || pos.getZ() >= r.z + r.sz) continue;
                int index = r.indices[r.offset(pos.getX(), pos.getY(), pos.getZ())];
                IBlockState state = world.getBlockState(pos);
                if (index < 0 || !state.getBlock().hasTileEntity(state) || palette.get(index).getBlock() != state.getBlock()) continue;
                TileEntity te = world.getTileEntity(pos);
                if (te == null) {
                    te = TileEntity.create(world, tile);
                    if (te != null) world.setTileEntity(pos.toImmutable(), te);
                } else {
                    te.readFromNBT(tile);
                }
                if (te != null) {
                    te.markDirty();
                    world.notifyBlockUpdate(pos, state, state, 3);
                }
            }
            for (NBTTagCompound tag : r.entities) {
                NBTTagCompound copy = tag.copy();
                // the copy gets its own identity: the original may still exist (a clone, or a move that has not deleted yet)
                copy.setUniqueId("UUID", UUID.randomUUID());
                Entity entity = EntityList.createEntityFromNBT(copy, world);
                if (entity != null) world.spawnEntity(entity);
            }
        }
    }

    // ---- save ----

    private static final class SaveJob extends Job {
        private final List<MessageSaveRequest.Box> boxes;
        private final int flags;
        private final List<RemoteBlob.Region> regions = new ArrayList<>();
        private final Set<UUID> savedEntities = new HashSet<>();
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int box, at;
        private List<byte[]> slices;
        private int sent;

        SaveJob(UUID owner, long id, int dimension, MessageSaveRequest request) {
            super(owner, id, dimension);
            this.boxes = request.boxes;
            this.flags = request.flags;
            long volume = 0;
            for (MessageSaveRequest.Box b : boxes) {
                int[] c = b.corners;
                volume += (long) (Math.abs(c[3] - c[0]) + 1) * (Math.abs(c[4] - c[1]) + 1) * (Math.abs(c[5] - c[2]) + 1);
            }
            this.total = (int) Math.min(Integer.MAX_VALUE, volume);
        }

        @Override boolean isEdit() { return false; }

        @Override boolean step(WorldServer world, long deadline) throws Exception {
            int budget = ServerConfig.blocksPerTick * 2;
            int counter = 0;
            while (box < boxes.size() && budget > 0) {
                int[] c = boxes.get(box).corners;
                int minX = Math.min(c[0], c[3]), minY = Math.min(c[1], c[4]), minZ = Math.min(c[2], c[5]);
                int sx = Math.abs(c[3] - c[0]) + 1, sy = Math.abs(c[4] - c[1]) + 1, sz = Math.abs(c[5] - c[2]) + 1;
                if (at == 0) regions.add(new RemoteBlob.Region(boxes.get(box).name, minX, minY, minZ, sx, sy, sz));
                RemoteBlob.Region region = regions.get(box);
                int volume = sx * sy * sz;
                while (at < volume && budget > 0) {
                    int i = at++;
                    completed++;
                    budget--;
                    pos.setPos(minX + i % sx, minY + i / (sx * sz), minZ + (i / sx) % sz);
                    if (pos.getY() >= 0 && pos.getY() < 256) read(world, region);
                    if (timeUp(deadline, ++counter)) return false;
                }
                if (at >= volume) {
                    if ((flags & MessageSaveRequest.ENTITIES) != 0) readEntities(world, region);
                    box++;
                    at = 0;
                }
            }
            if (box < boxes.size()) return false;
            if (slices == null) {
                NBTTagCompound blob = new NBTTagCompound();
                blob.setTag("regions", RemoteBlob.regionList(regions));
                slices = Slices.split(RemoteBlob.gzip(blob), Slices.SLICE_BYTES);
                regions.clear();
            }
            EntityPlayerMP player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(owner);
            for (int n = 0; n < SLICES_PER_TICK && sent < slices.size() && player != null; n++, sent++) {
                Net.CHANNEL.sendTo(new MessageSaveData(id, sent, slices.size(), slices.get(sent)), player);
            }
            affected = slices.size();
            return sent >= slices.size();
        }

        private void read(WorldServer world, RemoteBlob.Region region) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == Blocks.AIR) return;
            if ((flags & MessageSaveRequest.BLOCKS) != 0) region.set(pos.getX(), pos.getY(), pos.getZ(), StateStrings.toString(state));
            if ((flags & MessageSaveRequest.TILES) != 0 && state.getBlock().hasTileEntity(state)) {
                TileEntity te = world.getTileEntity(pos);
                if (te != null) region.tiles.add(te.writeToNBT(new NBTTagCompound()));
            }
        }

        private void readEntities(WorldServer world, RemoteBlob.Region region) {
            AxisAlignedBB bb = new AxisAlignedBB(region.x, region.y, region.z, region.x + region.sx, region.y + region.sy, region.z + region.sz);
            for (Entity entity : world.getEntitiesInAABBexcluding(null, bb, e -> !(e instanceof EntityPlayer))) {
                // only entities whose origin is inside the box, each once
                if (entity.posX < bb.minX || entity.posX > bb.maxX || entity.posY < bb.minY || entity.posY > bb.maxY
                    || entity.posZ < bb.minZ || entity.posZ > bb.maxZ || !savedEntities.add(entity.getUniqueID())) continue;
                NBTTagCompound tag = new NBTTagCompound();
                if (entity.writeToNBTOptional(tag)) region.entities.add(tag);
            }
        }
    }
}
