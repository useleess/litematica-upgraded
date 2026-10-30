package com.useleess.litematicaupgraded.mixin;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ListenableFuture;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;

import com.useleess.litematicaupgraded.malilib.render.buffer.VertexBuilder;
import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkCompileTaskGeneratorSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderWorkerLitematica;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.perf.PerfChunk;
import com.useleess.litematicaupgraded.perf.PerfGuard;
import com.useleess.litematicaupgraded.perf.PerfMath;
import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import com.useleess.litematicaupgraded.litematica.render.schematic.VertexBuilderCache;
import com.useleess.litematicaupgraded.perf.PerfConfigs;
import com.useleess.litematicaupgraded.perf.PerfDispatcher;

/**
 * Litematica for 1.12 already builds schematic chunks on worker threads. This sets their number from the settings and
 * stops the freeze when the renderers are reloaded while builds are running (from Litematica Turbo).
 */
@Mixin(value = ChunkRenderDispatcherLitematica.class, remap = false)
public abstract class MixinPerfDispatcher implements PerfDispatcher
{
    @Shadow @Final private static ThreadFactory THREAD_FACTORY;
    @Shadow @Final private List<Thread> listWorkerThreads;
    @Shadow @Final private List<ChunkRenderWorkerLitematica> listThreadedWorkers;
    @Shadow @Final private PriorityBlockingQueue<ChunkCompileTaskGeneratorSchematic> queueChunkUpdates;
    @Shadow @Final private BlockingQueue<VertexBuilderCache> queueFreeRenderBuilders;

    @Shadow public abstract boolean runChunkUploads(long finishTimeNano);
    @Shadow public abstract VertexBuilderCache allocateRenderBuilder() throws InterruptedException;

    /**
     * The thread count Litematica picks for itself (the CPU count, limited by memory). The setting replaces it when it is not
     * automatic (0); Litematica only starts worker threads for a count above 1, so 1 means "builds on the render thread".
     */
    @ModifyVariable(method = "<init>", at = @At("STORE"), index = 2)
    private int perf$threadCount(int original)
    {
        int wanted = PerfConfigs.BUILD_THREADS.getIntegerValue();
        return wanted > 0 ? wanted : PerfConfigs.AUTO_WORKERS.getBooleanValue() ? PerfMath.workers(perf$cores(), PerfMath.memoryThreadCap(Runtime.getRuntime().maxMemory())) : original;
    }

    /** The CPU count the automatic worker count is made from (tests may pretend another one). */
    @org.spongepowered.asm.mixin.Unique
    private static int perf$cores()
    {
        int forced = Integer.getInteger("litematicaupgraded.perf.cores", 0);
        return forced > 0 ? forced : Runtime.getRuntime().availableProcessors();
    }

    /**
     * Litematica only starts worker threads for a count above 1. With the automatic count on a 2-core PC (cores - 1 = 1)
     * one worker still runs, so the render thread never builds; an explicit setting of 1 keeps meaning "on the render thread".
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void perf$atLeastOneWorker(CallbackInfo ci)
    {
        boolean auto = PerfConfigs.BUILD_THREADS.getIntegerValue() == 0 && PerfConfigs.AUTO_WORKERS.getBooleanValue();

        if (auto && this.listWorkerThreads.isEmpty() && PerfConfigs.THREADED_BUILD.getBooleanValue())
        {
            ChunkRenderWorkerLitematica worker = new ChunkRenderWorkerLitematica((ChunkRenderDispatcherLitematica) (Object) this);
            Thread thread = THREAD_FACTORY.newThread(worker);
            thread.start();
            this.listThreadedWorkers.add(worker);
            this.listWorkerThreads.add(thread);
        }
    }

    /** Uploads made on the render thread are counted per piece and layer (batched regions notice changes, the complete copy counts its memory). */
    @Inject(method = "uploadChunkBlocks", at = @At("HEAD"))
    private void perf$noteUpload(BlockRenderLayer layer, VertexBuilder builder, RenderChunkSchematicVbo renderChunk, CompiledChunk compiledChunk,
                                 double distanceSq, CallbackInfoReturnable<ListenableFuture<Object>> cir)
    {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread())
        {
            ((PerfChunk) renderChunk).perf$noteUpload(layer.ordinal(), builder.getByteBuffer().limit());
        }
    }

    /**
     * With Minecraft's "Use VBOs" off, Litematica draws display lists, but its upload to them was left empty, so nothing
     * was drawn. This records the built vertices into the piece's display list the way Minecraft does for its own chunks.
     */
    @Inject(method = "uploadDisplayList", at = @At("HEAD"), cancellable = true)
    private void perf$uploadDisplayList(VertexBuilder builder, int list, RenderChunk renderChunk, CallbackInfo ci)
    {
        ci.cancel();

        if (PerfGuard.isOff(PerfGuard.Feature.DISPLAY_LISTS))
        {
            builder.reset();
            return;
        }

        try
        {
            BufferBuilder buffer = ((IMixinVanillaWrappingVertexBuilder) builder).perf$getBuffer();

            if (list < 0)
            {
                buffer.reset();
                return;
            }

            GlStateManager.glNewList(list, GL11.GL_COMPILE);
            GlStateManager.pushMatrix();
            PERF_UPLOADER.draw(buffer);
            GlStateManager.popMatrix();
            GlStateManager.glEndList();
        }
        catch (Throwable t)
        {
            PerfGuard.fail(PerfGuard.Feature.DISPLAY_LISTS, t);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static final WorldVertexBufferUploader PERF_UPLOADER = new WorldVertexBufferUploader();

    /**
     * stopChunkUpdates() waits for every buffer cache to come back, but a worker only returns its cache after the render
     * thread has run its uploads. Keep running uploads while waiting, otherwise the game freezes when the renderers are
     * reloaded during a build.
     */
    @Redirect(method = "stopChunkUpdates", at = @At(value = "INVOKE", target = "Lcom/useleess/litematicaupgraded/litematica/render/schematic/ChunkRenderDispatcherLitematica;allocateRenderBuilder()Lcom/useleess/litematicaupgraded/litematica/render/schematic/VertexBuilderCache;"))
    private VertexBuilderCache perf$allocateWhileUploading(ChunkRenderDispatcherLitematica self) throws InterruptedException
    {
        if (this.listWorkerThreads.isEmpty())
        {
            return this.allocateRenderBuilder();
        }

        while (true)
        {
            VertexBuilderCache cache = this.queueFreeRenderBuilders.poll(5, TimeUnit.MILLISECONDS);

            if (cache != null)
            {
                return cache;
            }

            this.runChunkUploads(0L);
        }
    }

    @Override
    public int perf$threadCount()
    {
        return this.listWorkerThreads.size();
    }

    @Override
    public int perf$queuedTasks()
    {
        return this.queueChunkUpdates.size();
    }
}
