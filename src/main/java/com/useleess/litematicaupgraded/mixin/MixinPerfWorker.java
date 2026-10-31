package com.useleess.litematicaupgraded.mixin;

import java.util.concurrent.locks.ReentrantLock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockRenderLayer;

import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkCompileTaskGeneratorSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderWorkerLitematica;
import com.useleess.litematicaupgraded.litematica.render.schematic.CompiledChunkSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo.OverlayRenderType;
import com.useleess.litematicaupgraded.perf.PerfChunk;
import com.useleess.litematicaupgraded.perf.PerfStats;

/**
 * Runs each build / resort under the chunk's own lock, so a cancelled build still finishing on one thread never overlaps a
 * new build of the same chunk on another (they share scratch fields). Also times the builds and records what they made
 * (from Litematica Turbo).
 */
@Mixin(value = ChunkRenderWorkerLitematica.class, remap = false)
public abstract class MixinPerfWorker
{
    @Redirect(method = "processTask", at = @At(value = "INVOKE", target = "Lcom/useleess/litematicaupgraded/litematica/render/schematic/RenderChunkSchematicVbo;rebuildChunk(FFFLcom/useleess/litematicaupgraded/litematica/render/schematic/ChunkCompileTaskGeneratorSchematic;)V"))
    private void perf$rebuildChunk(RenderChunkSchematicVbo renderer, float x, float y, float z, ChunkCompileTaskGeneratorSchematic task)
    {
        boolean onMainThread = Minecraft.getMinecraft().isCallingFromMinecraftThread();
        ReentrantLock lock = ((PerfChunk) renderer).perf$buildLock();
        lock.lock();
        long start = System.nanoTime();

        try
        {
            renderer.rebuildChunk(x, y, z, task);

            if (((PerfChunk) renderer).perf$isComplete() == false)
            {
                perf$recordIfCurrent(renderer, task, onMainThread);
            }
        }
        catch (RuntimeException e)
        {
            // The chunk renderer was discarded (world unloaded, ghost move) while this cancelled build was still running on a
            // worker; its result is thrown away anyway.
            if (onMainThread || task.isFinished() == false)
            {
                throw e;
            }
        }
        finally
        {
            lock.unlock();

            // the complete copy shown while moving is counted on its own (the normal builds must not run during a move)
            if (((PerfChunk) renderer).perf$isComplete())
            {
                PerfStats.recordCompleteBuild();
            }
            else
            {
                PerfStats.recordBuild(System.nanoTime() - start, onMainThread);
            }
        }
    }

    @Redirect(method = "processTask", at = @At(value = "INVOKE", target = "Lcom/useleess/litematicaupgraded/litematica/render/schematic/RenderChunkSchematicVbo;resortTransparency(FFFLcom/useleess/litematicaupgraded/litematica/render/schematic/ChunkCompileTaskGeneratorSchematic;)V"))
    private void perf$resortTransparency(RenderChunkSchematicVbo renderer, float x, float y, float z, ChunkCompileTaskGeneratorSchematic task)
    {
        ReentrantLock lock = ((PerfChunk) renderer).perf$buildLock();
        lock.lock();

        try
        {
            renderer.resortTransparency(x, y, z, task);
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * A build that was cancelled while it ran (finish(): a newer build was asked for, or the renderer was replaced) still runs
     * to its end, but its result is thrown away. It must not be recorded either: finishing after the newer build of the same
     * place (a replaced renderer has its own lock), it would overwrite that build's signature with an outdated one. Checked
     * under the task's lock, which finish() takes too.
     */
    private static void perf$recordIfCurrent(RenderChunkSchematicVbo renderer, ChunkCompileTaskGeneratorSchematic task, boolean onMainThread)
    {
        task.getLock().lock();

        try
        {
            if (task.getStatus() == ChunkCompileTaskGeneratorSchematic.Status.COMPILING)
            {
                perf$recordSignature(renderer, task.getCompiledChunk(), onMainThread);
            }
        }
        finally
        {
            task.getLock().unlock();
        }
    }

    /** What a build made: the vertex count of each layer and overlay, kept by position and by the thread that built it. */
    private static void perf$recordSignature(RenderChunkSchematicVbo renderer, CompiledChunkSchematic data, boolean onMainThread)
    {
        if (data == null)
        {
            return;
        }

        BlockRenderLayer[] layers = BlockRenderLayer.values();
        OverlayRenderType[] overlays = OverlayRenderType.values();
        int[] counts = new int[layers.length + overlays.length];

        for (int i = 0; i < layers.length; ++i)
        {
            counts[i] = data.getBlockBufferState(layers[i]) != null ? data.getBlockBufferState(layers[i]).getVertexCount() : 0;
        }

        for (int i = 0; i < overlays.length; ++i)
        {
            counts[layers.length + i] = data.getOverlayBufferState(overlays[i]) != null ? data.getOverlayBufferState(overlays[i]).getVertexCount() : 0;
        }

        PerfStats.recordSignature(renderer.getPosition().toLong(), counts, onMainThread);
    }
}
