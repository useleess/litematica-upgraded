package com.useleess.litematicaupgraded.mixin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;

import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.mixin.IMixinViewFrustum;
import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderContainerSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo.OverlayRenderType;
import com.useleess.litematicaupgraded.litematica.render.schematic.CompiledChunkSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.WorldWrap;
import com.useleess.litematicaupgraded.perf.BatchDraw;
import com.useleess.litematicaupgraded.perf.CompleteCopy;
import com.useleess.litematicaupgraded.perf.DrawStats;
import com.useleess.litematicaupgraded.perf.FarDetail;
import com.useleess.litematicaupgraded.perf.GhostMove;
import com.useleess.litematicaupgraded.perf.PerfGuard;
import com.useleess.litematicaupgraded.perf.PerfMath;
import com.useleess.litematicaupgraded.perf.PerfChunk;
import com.useleess.litematicaupgraded.perf.PerfConfigs;
import com.useleess.litematicaupgraded.perf.PerfDispatcher;
import com.useleess.litematicaupgraded.perf.PerfLive;
import com.useleess.litematicaupgraded.perf.PerfPlacementManager;
import com.useleess.litematicaupgraded.perf.PerfStats;
import com.useleess.litematicaupgraded.perf.PerfWorldRenderer;

/**
 * Near chunks are built on the build threads too (no render-thread builds), the ghost move draws shifted chunks while a
 * placement is moved, and the F3 line shows the build numbers (from Litematica Turbo).
 */
@Mixin(RenderGlobalSchematic.class)
public abstract class MixinPerfRenderGlobal implements PerfWorldRenderer
{
    @Shadow(remap = false) @Final private List<RenderChunkSchematicVbo> renderInfos;
    @Shadow(remap = false) private Set<RenderChunkSchematicVbo> chunksToUpdate;
    @Shadow(remap = false) private ChunkRenderDispatcherLitematica renderDispatcher;
    @Shadow(remap = false) private ViewFrustum viewFrustum;
    @Shadow(remap = false) private IRenderChunkFactory renderChunkFactory;
    @Shadow(remap = false) private WorldClient world;
    @Shadow(remap = false) private boolean displayListEntitiesDirty;
    @Shadow(remap = false) private double prevRenderSortX;
    @Shadow(remap = false) private double prevRenderSortY;
    @Shadow(remap = false) private double prevRenderSortZ;

    /** The real visible chunks while the ghosts are being drawn in their place. */
    @Unique
    private final List<RenderChunkSchematicVbo> perf$savedRenderInfos = new ArrayList<>();
    @Unique
    private final List<RenderChunkSchematicVbo> perf$drawList = new ArrayList<>();
    @Unique
    private boolean perf$listReady;
    @Unique
    private double perf$camX, perf$camY, perf$camZ;
    @Unique
    private double perf$savedSortX, perf$savedSortY, perf$savedSortZ, perf$sortXBefore;
    @Unique
    private boolean perf$swapped, perf$sortPaused;
    @Unique
    private long perf$lastSortNanos;
    @Unique
    private OverlayRenderType perf$overlayType;

    /** Chunks whose rebuild was requested while a build thread was still building them (render thread only). */
    @Unique
    private final Set<RenderChunkSchematicVbo> perf$deferred = new LinkedHashSet<>();

    /**
     * Stock Litematica rebuilds every chunk within 32 blocks of the camera right away on the render thread, which is the
     * stutter when standing in a big schematic. Hand those to the build threads too (nearest chunks are still built first).
     */
    @Redirect(method = {"setupTerrain", "updateChunks"}, expect = 2, at = @At(value = "INVOKE", remap = false, target = "Lcom/useleess/litematicaupgraded/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkNow(Lcom/useleess/litematicaupgraded/litematica/render/schematic/RenderChunkSchematicVbo;)Z"))
    private boolean perf$updateNow(ChunkRenderDispatcherLitematica dispatcher, RenderChunkSchematicVbo renderer)
    {
        if (PerfConfigs.THREADED_BUILD.getBooleanValue() == false || ((PerfDispatcher) dispatcher).perf$threadCount() == 0)
        {
            return dispatcher.updateChunkNow(renderer);
        }

        if (this.perf$deferIfBuilding(renderer))
        {
            return true;
        }

        return dispatcher.updateChunkLater(renderer);
    }

    @Redirect(method = "updateChunks", at = @At(value = "INVOKE", remap = false, target = "Lcom/useleess/litematicaupgraded/litematica/render/schematic/ChunkRenderDispatcherLitematica;updateChunkLater(Lcom/useleess/litematicaupgraded/litematica/render/schematic/RenderChunkSchematicVbo;)Z"))
    private boolean perf$updateLater(ChunkRenderDispatcherLitematica dispatcher, RenderChunkSchematicVbo renderer)
    {
        if (PerfConfigs.THREADED_BUILD.getBooleanValue() && this.perf$deferIfBuilding(renderer))
        {
            return true;
        }

        return dispatcher.updateChunkLater(renderer);
    }

    /**
     * Scheduling a rebuild waits on a lock that a running build of the same chunk holds for its whole duration, which would
     * freeze the render thread. Retry once that build has finished instead.
     */
    @Unique
    private boolean perf$deferIfBuilding(RenderChunkSchematicVbo renderer)
    {
        if (((PerfChunk) renderer).perf$isBuilding())
        {
            this.perf$deferred.add(renderer);
            return true;
        }

        return false;
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void perf$frameStart(long finishTimeNano, CallbackInfo ci)
    {
        if (PerfLive.consumeReload())
        {
            this.perf$resetDispatcher();
        }

        GhostMove.tick(this, this.perf$copiesQueued());

        if (this.perf$deferred.isEmpty())
        {
            return;
        }

        Iterator<RenderChunkSchematicVbo> it = this.perf$deferred.iterator();

        while (it.hasNext())
        {
            RenderChunkSchematicVbo renderer = it.next();

            if (((PerfChunk) renderer).perf$isBuilding() == false)
            {
                renderer.setNeedsUpdate(false);
                this.chunksToUpdate.add(renderer);
                it.remove();
            }
        }
    }

    /** Whether the placement manager still has loaded chunks to copy (chunks of unloaded areas wait in its list for ever). */
    @Unique
    private boolean perf$copiesQueued()
    {
        if (DataManager.getSchematicPlacementManager().hasPendingRebuilds() == false)
        {
            return false;
        }

        if (Configs.Generic.LOAD_ENTIRE_SCHEMATICS.getBooleanValue() || GameWrap.getClientWorld() == null)
        {
            return true;
        }

        return ((PerfPlacementManager) DataManager.getSchematicPlacementManager()).perf$hasCopyableChunks();
    }

    @Inject(method = {"setWorldAndLoadRenderers", "loadRenderers"}, at = @At("HEAD"))
    private void perf$clearDeferred(CallbackInfo ci)
    {
        this.perf$deferred.clear();
        GhostMove.reset();
        BatchDraw.clear();
    }

    // What a frame draws: while a placement is moved, the complete copy and the ghosts instead of the real chunks (which
    // are not rebuilt yet); otherwise the listed chunks. The list is made
    // once per frame and swapped in for each block layer and the overlay. The translucent re-sort is paused while moving
    // (NaN never passes its distance check) and, with the lazy sort on, until the camera moved a real distance.

    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void perf$frameBegin(CallbackInfo ci)
    {
        DrawStats.frame();
        BatchDraw.frame();
        PerfGuard.showNotes();
        this.perf$listReady = false;

        try
        {
            CompleteCopy.tick();
        }
        catch (RuntimeException e)
        {
            PerfGuard.fail(PerfGuard.Feature.FULL_COPY, e);
            CompleteCopy.forget();
        }
    }

    @Unique
    private void perf$prepareDrawList(Entity entity, double partialTicks)
    {
        if (this.perf$listReady)
        {
            return;
        }

        this.perf$listReady = true;
        this.perf$drawList.clear();
        this.perf$camX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        this.perf$camY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        this.perf$camZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        List<RenderChunkSchematicVbo> base = GhostMove.isActive() ? GhostMove.drawList() : this.renderInfos;
        DrawStats.listed(base.size());
        this.perf$drawList.addAll(base);
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DLnet/minecraft/entity/Entity;)I", remap = false, at = @At("HEAD"))
    private void perf$beforeLayer(BlockRenderLayer layer, double partialTicks, Entity entity, CallbackInfoReturnable<Integer> cir)
    {
        this.perf$prepareDrawList(entity, partialTicks);
        this.perf$swapIn();
        this.perf$sortPaused = false;
        this.perf$sortXBefore = this.prevRenderSortX;

        if (layer == BlockRenderLayer.TRANSLUCENT)
        {
            boolean pause = GhostMove.isActive();

            if (pause == false && PerfConfigs.LAZY_TRANSLUCENT_SORT.getBooleanValue() && PerfGuard.isOff(PerfGuard.Feature.TRANSLUCENT) == false)
            {
                double dx = entity.posX - this.prevRenderSortX, dy = entity.posY - this.prevRenderSortY, dz = entity.posZ - this.prevRenderSortZ;
                pause = PerfMath.shouldResort(dx * dx + dy * dy + dz * dz, System.nanoTime() - this.perf$lastSortNanos) == false;
            }

            if (pause)
            {
                this.perf$sortPaused = true;
                this.perf$savedSortX = this.prevRenderSortX;
                this.perf$savedSortY = this.prevRenderSortY;
                this.perf$savedSortZ = this.prevRenderSortZ;
                this.prevRenderSortX = Double.NaN;
                this.prevRenderSortY = Double.NaN;
                this.prevRenderSortZ = Double.NaN;
            }
        }
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DLnet/minecraft/entity/Entity;)I", remap = false, at = @At("RETURN"))
    private void perf$afterLayer(BlockRenderLayer layer, double partialTicks, Entity entity, CallbackInfoReturnable<Integer> cir)
    {
        if (layer == BlockRenderLayer.TRANSLUCENT)
        {
            if (this.perf$sortPaused)
            {
                this.prevRenderSortX = this.perf$savedSortX;
                this.prevRenderSortY = this.perf$savedSortY;
                this.prevRenderSortZ = this.perf$savedSortZ;
            }
            else if (Double.compare(this.prevRenderSortX, this.perf$sortXBefore) != 0)
            {
                this.perf$lastSortNanos = System.nanoTime();
                DrawStats.resort();
            }

            this.perf$sortPaused = false;
        }

        this.perf$swapOut();
    }

    @Inject(method = "renderBlockOverlays", remap = false, at = @At("HEAD"))
    private void perf$beforeOverlay(CallbackInfo ci)
    {
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();

        if (entity != null)
        {
            this.perf$prepareDrawList(entity, Minecraft.getMinecraft().getRenderPartialTicks());
            this.perf$swapIn();
        }
    }

    @Inject(method = "renderBlockOverlays", remap = false, at = @At("RETURN"))
    private void perf$afterOverlay(CallbackInfo ci)
    {
        this.perf$swapOut();
    }

    @Inject(method = "renderBlockOverlay", remap = false, at = @At("HEAD"))
    private void perf$overlayType(OverlayRenderType type, CallbackInfo ci)
    {
        this.perf$overlayType = type;
    }

    /** Far pieces are drawn without their outline lines (less detail far away); every overlay piece drawn is counted. */
    @Redirect(method = "renderBlockOverlay", remap = false, at = @At(value = "INVOKE", remap = false,
              target = "Lcom/useleess/litematicaupgraded/litematica/render/schematic/ChunkRenderContainerSchematic;addOverlayChunk(Lcom/useleess/litematicaupgraded/litematica/render/schematic/RenderChunkSchematicVbo;)V"))
    private void perf$addOverlayChunk(ChunkRenderContainerSchematic container, RenderChunkSchematicVbo renderChunk)
    {
        boolean outline = this.perf$overlayType == OverlayRenderType.OUTLINE;

        if (outline && FarDetail.isFar(renderChunk, this.perf$camX, this.perf$camZ))
        {
            DrawStats.farOutlineSkipped();
            return;
        }

        DrawStats.overlayDraw(outline);
        container.addOverlayChunk(renderChunk);
    }

    @Unique
    private void perf$swapIn()
    {
        if (this.perf$swapped == false)
        {
            this.perf$swapped = true;
            this.perf$savedRenderInfos.clear();
            this.perf$savedRenderInfos.addAll(this.renderInfos);
            this.renderInfos.clear();
            this.renderInfos.addAll(this.perf$drawList);
        }
    }

    @Unique
    private void perf$swapOut()
    {
        if (this.perf$swapped)
        {
            this.perf$swapped = false;
            this.renderInfos.clear();
            this.renderInfos.addAll(this.perf$savedRenderInfos);
            this.perf$savedRenderInfos.clear();
        }
    }

    @Inject(method = "getDebugInfoRenders", at = @At("RETURN"), cancellable = true)
    private void perf$appendStats(CallbackInfoReturnable<String> cir)
    {
        if (PerfConfigs.SHOW_STATS.getBooleanValue() && this.renderDispatcher != null)
        {
            PerfDispatcher info = (PerfDispatcher) this.renderDispatcher;
            cir.setReturnValue(cir.getReturnValue() + " | " + PerfStats.summary(info.perf$threadCount(), info.perf$queuedTasks()));
        }
    }

    @Override
    public boolean perf$isIdle()
    {
        if (this.renderDispatcher == null || this.chunksToUpdate.isEmpty() == false || this.perf$deferred.isEmpty() == false
            || this.renderDispatcher.hasChunkUpdates() == false) // true means the queues are empty
        {
            return false;
        }

        for (RenderChunkSchematicVbo renderer : this.perf$swapped ? this.perf$savedRenderInfos : this.renderInfos)
        {
            if (renderer.needsUpdate() || ((PerfChunk) renderer).perf$isBuilding())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Every chunk renderer that holds built buffers is taken out of the view frustum and replaced by a fresh renderer at the
     * same place; the old ones become the ghosts.
     */
    @Override
    public List<RenderChunkSchematicVbo> perf$detachChunkRenderers()
    {
        if (this.viewFrustum == null || this.world == null)
        {
            return null;
        }

        RenderChunk[] renderers = this.viewFrustum.renderChunks;
        List<RenderChunkSchematicVbo> detached = new ArrayList<>();

        for (int i = 0; i < renderers.length; ++i)
        {
            RenderChunkSchematicVbo old = (RenderChunkSchematicVbo) renderers[i];

            if (old.getChunkRenderData() != CompiledChunkSchematic.EMPTY)
            {
                net.minecraft.util.math.BlockPos origin = old.getPosition();
                RenderChunkSchematicVbo fresh = (RenderChunkSchematicVbo) this.renderChunkFactory.create(this.world, (RenderGlobalSchematic) (Object) this, i);
                fresh.setPosition(origin.getX(), origin.getY(), origin.getZ());
                // nothing is built here until the copy of the moved placement marks it again (the ghost is drawn meanwhile)
                fresh.clearNeedsUpdate();
                renderers[i] = fresh;
                detached.add(old);
            }
        }

        this.renderInfos.clear();
        this.chunksToUpdate.clear();
        this.perf$deferred.clear();
        this.displayListEntitiesDirty = true;
        return detached;
    }

    @Override
    public void perf$resetDispatcher()
    {
        GhostMove.reset();

        if (this.renderDispatcher != null)
        {
            this.renderDispatcher.stopChunkUpdates();
            this.renderDispatcher.stopWorkerThreads();
            this.renderDispatcher = null;
        }

        if (this.world != null)
        {
            ((RenderGlobalSchematic) (Object) this).loadRenderers();
        }
    }

    @Override
    public RenderChunkSchematicVbo perf$newRenderer(int x, int y, int z)
    {
        RenderChunkSchematicVbo r = (RenderChunkSchematicVbo) this.renderChunkFactory.create(this.world, (RenderGlobalSchematic) (Object) this, 0);
        r.setPosition(x, y, z);
        r.clearNeedsUpdate();
        return r;
    }

    @Override
    public ChunkRenderDispatcherLitematica perf$dispatcher()
    {
        return this.renderDispatcher;
    }

    @Override
    public List<RenderChunkSchematicVbo> perf$lastDrawList()
    {
        return this.perf$drawList;
    }

    @Override
    public List<RenderChunkSchematicVbo> perf$listedRenderers()
    {
        return this.renderInfos;
    }

    @Override
    public int perf$buildThreads()
    {
        return this.renderDispatcher == null ? 0 : ((PerfDispatcher) this.renderDispatcher).perf$threadCount();
    }

    @Override
    public boolean perf$chunkHasModels(int x, int y, int z)
    {
        if (this.viewFrustum == null)
        {
            return false;
        }

        RenderChunk chunk = ((IMixinViewFrustum) this.viewFrustum).invokeGetRenderChunk(new net.minecraft.util.math.BlockPos(x, y, z));
        return chunk instanceof RenderChunkSchematicVbo && ((RenderChunkSchematicVbo) chunk).getChunkRenderData().isEmpty() == false;
    }

    @Override
    public String perf$describeChunk(int x, int y, int z)
    {
        if (this.viewFrustum == null)
        {
            return "no view frustum";
        }

        RenderChunk chunk = ((IMixinViewFrustum) this.viewFrustum).invokeGetRenderChunk(new net.minecraft.util.math.BlockPos(x, y, z));

        if (chunk instanceof RenderChunkSchematicVbo == false)
        {
            return "renderer " + chunk;
        }

        RenderChunkSchematicVbo r = (RenderChunkSchematicVbo) chunk;
        return "renderer at " + r.getPosition() + " built=" + (r.getChunkRenderData() != CompiledChunkSchematic.EMPTY) + " empty=" + r.getChunkRenderData().isEmpty()
                + " needsUpdate=" + r.needsUpdate() + " listed=" + this.renderInfos.contains(r) + " listSize=" + this.renderInfos.size();
    }

    @Override
    public int perf$renderedWithData()
    {
        int n = 0;

        for (RenderChunkSchematicVbo renderer : this.perf$swapped ? this.perf$savedRenderInfos : this.renderInfos)
        {
            CompiledChunkSchematic data = renderer.getChunkRenderData();

            if (data != CompiledChunkSchematic.EMPTY && data.isEmpty() == false)
            {
                n++;
            }
        }

        return n;
    }
}
