package com.useleess.litematicaupgraded.preview;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import com.useleess.litematicaupgraded.litematica.Litematica;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;

/**
 * The 3D view of the schematic file selected in the schematic browser: it turns on its own, the left mouse button drags it around
 * and the wheel zooms. One view exists at a time (one browser screen). The model is built in sections on background threads (a big file
 * gets a rough copy first); the render thread only turns finished sections into display lists, a few milliseconds per frame, and draws
 * what is there. The last few built models are kept for going back to a file. When building, uploading or drawing fails, the view
 * switches itself off until the screen is closed. When the browser is no longer drawn it frees its GL resources.
 */
public final class PreviewView {
    public static final PreviewView INSTANCE = new PreviewView();
    /** Ticks without a draw after which the screen counts as closed. */
    private static final int CLOSED_AFTER_TICKS = 10;
    /** A longer pause between two frames (the screen was not drawn) does not count as turning time. */
    private static final long MAX_FRAME_GAP_MS = 100;
    private static final AtomicInteger CANCELLED_BUILDS = new AtomicInteger();
    private static ExecutorService executor;

    /** One model being built for one file. */
    private static final class Job {
        final Path file;
        final long modified;
        volatile boolean cancelled;
        volatile PreviewModel rough, full, detail;
        volatile Throwable error;
        final PreviewTiming timing = new PreviewTiming();

        Job(Path file, long modified) {
            this.file = file;
            this.modified = modified;
        }

        String key() {
            return file + "|" + modified;
        }

        /** The newest model of this file, the full one once it exists. */
        PreviewModel newest() {
            return full != null ? full : rough;
        }

        String detailKey() {
            return key() + "|1:1";
        }
    }

    @Nullable private RuntimeException failNextDraw;
    @Nullable private Job job;
    /** The model drawn now, and the full one being uploaded meanwhile while a rough one is drawn. */
    @Nullable private PreviewModel model, incoming;
    /** The model of every block of a shrunk file, drawn instead of the shrunk one when the view is zoomed in far enough. */
    @Nullable private PreviewModel detailModel;
    private final LruCache<String, PreviewModel> cache = new LruCache<>(PreviewMath.CACHE_SIZE, PreviewModel::free);
    private boolean off;
    /** Source blocks per block edge and vertices of the model drawn in the last frame. */
    private int drawnStep, drawnVertices;
    private int ticks;
    private int lastDrawTick = Integer.MIN_VALUE / 2;
    private long framesDrawn;
    private long maxDrawNanos;
    /** The turn the player gave the view, on top of the automatic turning. */
    private float yawBase = PreviewMath.YAW_DEGREES;
    private float pitch = PreviewMath.PITCH_DEGREES;
    private float zoom = 1f;
    private long turningMillis;
    private long lastFrameMillis;
    private boolean dragging;
    private boolean turnPaused;
    private int dragX, dragY;
    /** x, y, width, height of the last draw. */
    private int[] bounds = new int[4];
    private final FloatBuffer matrix = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();

    private PreviewView() {}

    // ------------------------------------------------------------------------------------------ what the screen tells it

    /** The browser selected another file (or none). */
    public void show(@Nullable Path file) {
        long modified = file == null ? 0 : modifiedOf(file);
        if (job != null && file != null && job.file.equals(file) && job.modified == modified) return;
        clear();
        if (file == null) return;
        Job next = new Job(file, modified);
        job = next;
        PreviewModel kept = cache.get(next.key());
        if (kept != null) {
            next.full = kept;
            next.detail = cache.get(next.detailKey());
            next.timing.fromCache = true;
            return;
        }
        executor().execute(() -> run(next));
    }

    private static long modifiedOf(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (java.io.IOException e) {
            return 0;
        }
    }

    private static synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Litematica Upgraded preview");
                t.setDaemon(true);
                return t;
            });
        }
        return executor;
    }

    private static void run(Job j) {
        try {
            if (j.cancelled) throw new PreviewBuilder.CancelledException();
            PreviewBuilder.build(j.file, () -> j.cancelled, j.timing, m -> j.rough = m, m -> j.full = m, m -> j.detail = m);
        } catch (PreviewBuilder.CancelledException | InterruptedException e) {
            CANCELLED_BUILDS.incrementAndGet();
        } catch (Throwable t) {
            Litematica.LOGGER.warn("Litematica Upgraded: no 3D preview for {}", j.file, t);
            j.error = t;
        }
    }

    /** Stops a build in progress, lets go of the shown model (a kept one stays in the cache) and puts the view back to its start turn and zoom. Render thread. */
    public void clear() {
        if (job != null) job.cancelled = true;
        job = null;
        drop(model);
        drop(incoming);
        drop(detailModel);
        model = null;
        incoming = null;
        detailModel = null;
        yawBase = PreviewMath.YAW_DEGREES;
        pitch = PreviewMath.PITCH_DEGREES;
        zoom = 1f;
        turningMillis = 0;
        lastFrameMillis = 0;
        dragging = false;
        turnPaused = false;
    }

    private void drop(@Nullable PreviewModel dropped) {
        if (dropped != null && !cache.containsValue(dropped)) dropped.free();
    }

    /** Clears and frees the kept models too. */
    private void freeAll() {
        clear();
        cache.clear();
    }

    /** The browser is gone: frees everything, and the next screen may try again after an error. */
    public void release() {
        freeAll();
        off = false;
    }

    // ------------------------------------------------------------------------------------------ mouse

    /** Whether a point (GUI pixels) is inside the rectangle the view was last drawn in. */
    public boolean contains(int mouseX, int mouseY) {
        return model != null && mouseX >= bounds[0] && mouseY >= bounds[1] && mouseX < bounds[0] + bounds[2] && mouseY < bounds[1] + bounds[3];
    }

    /** The left button went down: starts turning the model by hand when the point is inside the view. */
    public boolean press(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        dragging = true;
        dragX = mouseX;
        dragY = mouseY;
        return true;
    }

    /** The mouse moved with the button held: turns (sideways) and tilts (up and down) the model. */
    public void dragTo(int mouseX, int mouseY) {
        if (!dragging) return;
        yawBase = PreviewMath.dragYaw(yawBase, mouseX - dragX);
        pitch = PreviewMath.dragPitch(pitch, mouseY - dragY);
        dragX = mouseX;
        dragY = mouseY;
    }

    public void releaseMouse() {
        dragging = false;
    }

    /** The wheel moved over the view: zooms in (positive) or out. */
    public boolean scroll(int mouseX, int mouseY, double wheel) {
        if (!contains(mouseX, mouseY)) return false;
        zoom = detailModel != null && detailModel.complete()
                ? PreviewMath.zoomed(zoom, wheel, PreviewMath.maxZoom(PreviewMath.scaleToFit(detailModel.sizeX, detailModel.sizeY, detailModel.sizeZ,
                        bounds[2], bounds[3], pitch)))
                : PreviewMath.zoomed(zoom, wheel);
        return true;
    }

    /** Tests: stops the automatic turning with the view at this turn and tilt (until the view is cleared). */
    public void holdViewForTest(float yawDegrees, float pitchDegrees) {
        yawBase = yawDegrees;
        pitch = pitchDegrees;
        turningMillis = 0;
        turnPaused = true;
    }

    /** The turn around the vertical axis now, in degrees. */
    private float yaw() {
        return yawBase + PreviewMath.angle(turningMillis);
    }

    // ------------------------------------------------------------------------------------------ drawing

    /** Draws the view of {@code file} (or its short text) in the rectangle. Render thread, once per frame of the browser. */
    public void draw(Path file, int x, int y, int width, int height, float z, int mouseX, int mouseY) {
        lastDrawTick = ticks;
        bounds = new int[] {x, y, width, height};
        if (!PreviewConfigs.ENABLED.getBooleanValue()) {
            freeAll();
            return;
        }
        if (off) return;
        if (job == null) show(file);
        long started = System.nanoTime();
        try {
            if (failNextDraw != null) {
                RuntimeException e = failNextDraw;
                failNextDraw = null;
                throw e;
            }
            Job j = job;
            uploadFinished(j);
            if (model != null && model.uploadedPieces() > 0) {
                drawModel(model, x, y, width, height, z, mouseX, mouseY);
                framesDrawn++;
                noteFrame(j);
            } else if (j.error != null) {
                drawText(x, y, width, height, "litematica_upgraded.preview.none");
            } else {
                PreviewModel building = j.newest();
                drawText(x, y, width, height, "litematica_upgraded.preview.building");
                drawCorner(x + 3, y + height - 11, (building == null ? 0 : building.percent()) + "%");
            }
        } catch (Throwable t) {
            Litematica.LOGGER.error("Litematica Upgraded: the 3D preview failed and is off until the screen is closed", t);
            off = true;
            clear();
            drawText(x, y, width, height, "litematica_upgraded.preview.none");
        }
        maxDrawNanos = Math.max(maxDrawNanos, System.nanoTime() - started);
    }

    /** Takes the newest model of the job and turns its finished sections into display lists within the frame's budget; a full model replaces a rough one when it is all there. */
    private void uploadFinished(Job j) {
        long deadline = System.nanoTime() + PreviewMath.UPLOAD_BUDGET_MS * 1_000_000;
        PreviewModel newest = j.newest();
        if (model == null) model = j.rough != null ? j.rough : newest;
        if (model != null && newest != model) incoming = newest;
        if (model == null) return;
        if (!model.complete()) model.upload(deadline);
        if (incoming != null) {
            incoming.upload(deadline);
            if (incoming.complete()) {
                drop(model);
                model = incoming;
                incoming = null;
            }
        }
        if (model.full && model.complete() && !cache.containsValue(model)) cache.put(j.key(), model);
        uploadDetail(j, deadline);
    }

    /** Once the shrunk model is all there, turns the sections of the full model into display lists with what is left of the frame's budget. */
    private void uploadDetail(Job j, long deadline) {
        if (j.detail == null || !model.full || !model.complete()) return;
        detailModel = j.detail;
        if (!detailModel.complete() && System.nanoTime() < deadline) detailModel.upload(deadline);
        if (detailModel.complete() && !cache.containsValue(detailModel)) cache.put(j.detailKey(), detailModel);
        PreviewTiming timing = j.timing;
        if (detailModel.complete() && timing.detailUploadMs < 0) {
            timing.detailUploadMs = detailModel.uploadMillis();
            timing.logDetailOnce(j.file.getFileName().toString());
        }
    }

    /** The numbers of the stages: the first picture, and the complete model. */
    private void noteFrame(Job j) {
        PreviewTiming timing = j.timing;
        if (timing.firstFrameMs < 0) {
            timing.firstFrameMs = timing.elapsedMs();
            timing.rough = !model.full;
        }
        if (model.full && model.complete() && timing.completeMs < 0) {
            timing.uploadMs = model.uploadMillis();
            timing.completeMs = timing.elapsedMs();
            timing.logOnce(j.file.getFileName().toString(), model.blockCount, model.step);
        }
    }

    private static void drawText(int x, int y, int width, int height, String key) {
        String text = StringUtils.translate(key);
        net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawString(text, x + (width - font.getStringWidth(text)) / 2, y + height / 2 - 4, 0xFFA0A0A0);
    }

    private static void drawCorner(int x, int y, String text) {
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, x, y, 0xFFAAAAAA);
    }

    /** The full model once it is built, at every zoom; until then the given (shrunk or rough) one. */
    private PreviewModel drawnModel(PreviewModel shrunk) {
        return PreviewMath.modelToDraw(shrunk, detailModel, detailModel != null && detailModel.complete());
    }

    private void drawModel(PreviewModel shown, int x, int y, int width, int height, float z, int mouseX, int mouseY) {
        PreviewModel m = drawnModel(shown);
        drawnStep = m.step;
        drawnVertices = m.vertexCount();
        long now = System.currentTimeMillis();
        if (dragging) {
            if (Mouse.isButtonDown(0)) dragTo(mouseX, mouseY);
            else dragging = false;
        } else if (lastFrameMillis > 0 && !turnPaused) {
            turningMillis += Math.min(now - lastFrameMillis, MAX_FRAME_GAP_MS);
        }
        lastFrameMillis = now;

        int[] box = scissorBox(x, y, width, height);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] previous = new int[4];
        if (scissor) {
            java.nio.IntBuffer buffer = org.lwjgl.BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, buffer);
            buffer.get(previous);
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), blend = GL11.glIsEnabled(GL11.GL_BLEND), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        int[] blendFunc = {GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)};
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(box[0], box[1], box[2], box[3]);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
            GlStateManager.color(1f, 1f, 1f, 1f);
            // the block atlas is mipmapped by the game's own setting (min filter NEAREST_MIPMAP_LINEAR), so tiny blocks sample smoothly
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            float scale = PreviewMath.scaleToFit(m.sizeX, m.sizeY, m.sizeZ, width, height, pitch) * zoom;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + width / 2f, y + height / 2f, z + 100f);
            GlStateManager.scale(scale, -scale, scale);
            GlStateManager.rotate(pitch, 1f, 0f, 0f);
            GlStateManager.rotate(yaw(), 0f, 1f, 0f);
            GlStateManager.translate(-m.centerX, -m.centerY, -m.centerZ);
            m.drawSolid();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(false);
            m.drawTranslucent();
            GlStateManager.popMatrix();
        } finally {
            restore(scissor, previous, depth, depthMask, depthFunc, cull, lighting, blend, alpha, texture);
            GlStateManager.alphaFunc(alphaFunc, alphaRef);
            GlStateManager.tryBlendFuncSeparate(blendFunc[0], blendFunc[1], blendFunc[2], blendFunc[3]);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
        if (m.step > 1) {
            String ratio = "1:" + m.step;
            drawCorner(x + width - 3 - Minecraft.getMinecraft().fontRenderer.getStringWidth(ratio), y + height - 11, ratio);
        }
    }

    private static void restore(boolean scissor, int[] previous, boolean depth, boolean depthMask, int depthFunc,
                                boolean cull, boolean lighting, boolean blend, boolean alpha, boolean texture) {
        if (scissor) GL11.glScissor(previous[0], previous[1], previous[2], previous[3]);
        else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (depth) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
        GlStateManager.depthMask(depthMask);
        GlStateManager.depthFunc(depthFunc);
        if (cull) GlStateManager.enableCull();
        else GlStateManager.disableCull();
        if (lighting) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
        if (blend) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
        if (alpha) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
        if (texture) GlStateManager.enableTexture2D();
        else GlStateManager.disableTexture2D();
    }

    /** The pixel box of the rectangle, from the matrices in use now (so any GUI scale or screen scaling is right). */
    private int[] scissorBox(int x, int y, int width, int height) {
        float[] modelView = matrixOf(GL11.GL_MODELVIEW_MATRIX), projection = matrixOf(GL11.GL_PROJECTION_MATRIX);
        java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        return ScissorMath.toPixels(modelView, projection, new int[] {viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3)}, x, y, width, height);
    }

    private float[] matrixOf(int which) {
        matrix.clear();
        GL11.glGetFloat(which, matrix);
        float[] values = new float[16];
        matrix.get(values);
        return values;
    }

    // ------------------------------------------------------------------------------------------ closing, and numbers for tests

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if ((job != null || off) && ticks - lastDrawTick > CLOSED_AFTER_TICKS) release();
    }

    /** Frames of the model drawn so far (tests). */
    public long framesDrawn() {
        return framesDrawn;
    }

    /** The turn around the vertical axis now, in degrees (tests). */
    public float lastAngle() {
        return yaw();
    }

    public float pitch() {
        return pitch;
    }

    public float zoom() {
        return zoom;
    }

    /** The rectangle the view was last drawn in: x, y, width, height (tests find the mouse spots from it). */
    public int[] viewBounds() {
        return bounds.clone();
    }

    /** The longest time one draw call of the view took, in milliseconds (tests). */
    public long maxDrawMillis() {
        return maxDrawNanos / 1_000_000;
    }

    public void resetStats() {
        maxDrawNanos = 0;
        framesDrawn = 0;
    }

    /** Vertices of the shown model, 0 while none is ready (tests). */
    public int vertexCount() {
        return model == null ? 0 : model.vertexCount();
    }

    /** Vertices of the translucent blocks (stained glass) in the shown model (tests). */
    public int translucentVertexCount() {
        return model == null ? 0 : model.translucentVertexCount();
    }

    /** Blocks in the shown model (tests). */
    public long blockCount() {
        return model == null ? 0 : model.blockCount;
    }

    /** Source blocks per shown block edge: 1 = the file as it is, more = a shrunk copy labelled 1:N (tests). */
    public int step() {
        return model == null ? 0 : model.step;
    }

    /** Whether the shown model carries texture coordinates, so it is drawn with the block atlas (tests). */
    public boolean textured() {
        return model != null && model.textured();
    }

    /** Whether the full model (not the rough copy) is built and drawn (tests). */
    public boolean complete() {
        return model != null && model.full && model.complete();
    }

    /** Source blocks per block edge of the model drawn in the last frame: 1 = every block of the file (tests). */
    public int drawnStep() {
        return drawnStep;
    }

    /** Vertices of the model drawn in the last frame (tests). */
    public int drawnVertexCount() {
        return drawnVertices;
    }

    /** Whether the model of every block of a shrunk file is built and uploaded (tests). */
    public boolean detailComplete() {
        return detailModel != null && detailModel.complete();
    }

    /** Vertices of the full model, 0 while there is none (tests). */
    public int detailVertexCount() {
        return detailModel == null ? 0 : detailModel.vertexCount();
    }

    /** Whether building the full model is over, with or without a model (it is left out over the size cap) (tests). */
    public boolean detailSettled() {
        return job != null && job.timing.detailBuildMs >= 0;
    }

    /** Builds stopped because another file was chosen or the screen closed, since the game started (tests). */
    public static int cancelledBuilds() {
        return CANCELLED_BUILDS.get();
    }

    /** The stage times of the shown file's preview (tests); null when none is selected. */
    @Nullable
    public PreviewTiming timing() {
        return job == null ? null : job.timing;
    }

    /** The error that stopped building the model, null when none (tests). */
    @Nullable
    public Throwable error() {
        return job == null ? null : job.error;
    }

    /** Tests: the most vertices the full model may have; a schematic over it keeps its shrunk model. */
    public static void capVerticesForTest(long vertices) {
        PreviewBuilder.setVertexCapForTest(vertices);
    }

    public static void resetVertexCap() {
        PreviewBuilder.resetVertexCap();
    }

    /** Tests: the next draw throws this, as a failing graphics driver would. */
    public void failNextDrawForTest(RuntimeException e) {
        failNextDraw = e;
    }

    public boolean switchedOff() {
        return off;
    }

    /** How many GL display lists the previews hold now (tests). */
    public int glResources() {
        return PreviewModel.liveLists();
    }
}
