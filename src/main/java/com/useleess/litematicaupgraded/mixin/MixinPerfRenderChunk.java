package com.useleess.litematicaupgraded.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

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
import net.minecraft.tileentity.TileEntity;

import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkCacheSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkCompileTaskGeneratorSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.CompiledChunkSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;
import com.useleess.litematicaupgraded.perf.EmptyChunkView;
import com.useleess.litematicaupgraded.perf.PerfChunk;
import com.useleess.litematicaupgraded.perf.PerfConfigs;
import com.useleess.litematicaupgraded.perf.PerfStats;

@Mixin(value = RenderChunkSchematicVbo.class, remap = false)
public abstract class MixinPerfRenderChunk implements PerfChunk
{
    @Shadow @Final private Set<TileEntity> setTileEntities;
    @Shadow private ChunkCacheSchematic schematicWorldView;
    @Shadow private ChunkCacheSchematic clientWorldView;
    @Shadow private CompiledChunkSchematic schematicChunkRenderData;
    @Shadow private ChunkCompileTaskGeneratorSchematic compileTask;

    @Shadow private boolean overlayEnabled;
    @Shadow @Final private List<IntBoundingBox> boxes;

    @Unique
    private final ReentrantLock perf$buildLock = new ReentrantLock();
    @Unique
    private volatile boolean perf$complete;
    @Unique
    private volatile List<IntBoundingBox> perf$completeBoxes;
    @Unique
    private final int[] perf$generations = new int[4];
    @Unique
    private final long[] perf$uploaded = new long[4];
    @Unique
    private volatile int perf$dataGeneration;

    @Override
    public boolean perf$isComplete()
    {
        return this.perf$complete;
    }

    @Override
    public void perf$makeComplete(List<IntBoundingBox> boxes)
    {
        this.perf$completeBoxes = new ArrayList<>(boxes);
        this.perf$complete = true;
    }

    @Override
    public int perf$generation(int layer)
    {
        return this.perf$generations[layer];
    }

    @Override
    public void perf$noteUpload(int layer, int bytes)
    {
        this.perf$generations[layer]++;
        this.perf$uploaded[layer] = bytes;
    }

    @Override
    public long perf$uploadedBytes()
    {
        long n = 0;

        for (long b : this.perf$uploaded)
        {
            n += b;
        }

        return n;
    }

    @Override
    public int perf$dataGeneration()
    {
        return this.perf$dataGeneration;
    }

    /** A new build: the upload sizes are counted again from zero (a layer the build leaves empty uploads nothing). */
    @Inject(method = "rebuildChunk", at = @At("HEAD"))
    private void perf$resetUploadSizes(float x, float y, float z, ChunkCompileTaskGeneratorSchematic generator, CallbackInfo ci)
    {
        java.util.Arrays.fill(this.perf$uploaded, 0L);
    }

    @Inject(method = "setChunkRenderData", at = @At("RETURN"))
    private void perf$countData(CompiledChunkSchematic data, CallbackInfo ci)
    {
        this.perf$dataGeneration++;
    }

    /**
     * A complete-copy piece compares with an all-air world: every schematic block is built, and it has no overlay (the
     * normal pieces bring the overlay colours back after a move). Its boxes are the placement's from before the move
     * (Litematica's are already the moved ones, while the schematic world still holds the blocks at the old place).
     */
    @Inject(method = "rebuildWorldView", at = @At("RETURN"))
    private void perf$completeView(CallbackInfo ci)
    {
        if (this.perf$complete)
        {
            RenderChunkSchematicVbo self = (RenderChunkSchematicVbo) (Object) this;
            this.clientWorldView = new EmptyChunkView(Minecraft.getMinecraft().world, self.getPosition());
            this.overlayEnabled = false;

            synchronized (this.boxes)
            {
                this.boxes.clear();
                this.boxes.addAll(this.perf$completeBoxes);
            }
        }
    }

    @Override
    public ReentrantLock perf$buildLock()
    {
        return this.perf$buildLock;
    }

    @Override
    public void perf$cancelCompile()
    {
        // the same as the chunk's own finishCompileTask (which is a name the game's RenderChunk also has)
        RenderChunkSchematicVbo self = (RenderChunkSchematicVbo) (Object) this;
        self.getLockCompileTask().lock();

        try
        {
            if (this.compileTask != null && this.compileTask.getStatus() != ChunkCompileTaskGeneratorSchematic.Status.DONE)
            {
                this.compileTask.finish();
                this.compileTask = null;
            }
        }
        finally
        {
            self.getLockCompileTask().unlock();
        }
    }

    @Override
    public void perf$forgetTileEntities(RenderGlobalSchematic worldRenderer)
    {
        worldRenderer.updateTileEntities(new ArrayList<>(this.setTileEntities), Collections.<TileEntity>emptyList());
        this.setTileEntities.clear();
    }

    /**
     * Counts every build as one 16-high slice looked at, and as skipped when nothing is in it (the build then ends at once).
     */
    @Inject(method = "rebuildChunk", at = @At("HEAD"))
    private void perf$countSlice(float x, float y, float z, ChunkCompileTaskGeneratorSchematic generator, CallbackInfo ci)
    {
        if (this.schematicWorldView != null && this.clientWorldView != null)
        {
            PerfStats.recordSlice(PerfConfigs.SKIP_EMPTY_SLICES.getBooleanValue() && this.schematicWorldView.isEmpty() && this.clientWorldView.isEmpty());
        }
    }

    /**
     * With the speed-up off, an empty slice is walked cell by cell like a full one (two block lookups per cell); on, the
     * build ends at once. Skipped cells would have produced nothing.
     */
    @Redirect(method = "rebuildChunk", expect = 2, at = @At(value = "INVOKE", target = "Lcom/useleess/litematicaupgraded/litematica/render/schematic/ChunkCacheSchematic;isEmpty()Z"))
    private boolean perf$isEmpty(ChunkCacheSchematic view)
    {
        return PerfConfigs.SKIP_EMPTY_SLICES.getBooleanValue() ? view.isEmpty() : false;
    }

    /**
     * Stock hasOverlay() is a field the build clears at its start, so with threaded builds the overlay of a chunk would
     * blink off while it rebuilds. Answer from the finished data instead.
     */
    @Inject(method = "hasOverlay", at = @At("HEAD"), cancellable = true)
    private void perf$hasOverlayFromBuiltData(CallbackInfoReturnable<Boolean> cir)
    {
        if (PerfConfigs.THREADED_BUILD.getBooleanValue())
        {
            cir.setReturnValue(this.schematicChunkRenderData.isOverlayEmpty() == false);
        }
    }
}
