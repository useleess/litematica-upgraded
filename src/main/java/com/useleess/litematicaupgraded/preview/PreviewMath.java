package com.useleess.litematicaupgraded.preview;

/** The pure rules of the 3D preview (unit tested): how it turns, drags and zooms, how big it is drawn, which size limits apply. */
public final class PreviewMath {
    /** One full turn of the automatic turning takes this long. */
    public static final long TURN_MS = 12000;
    /** The view starts turned by this many degrees around the vertical axis. */
    public static final float YAW_DEGREES = 45f;
    /** The view starts looking down on the schematic by this many degrees. */
    public static final float PITCH_DEGREES = 30f;
    /** Degrees the model turns per GUI pixel the mouse is dragged. */
    public static final float DRAG_DEGREES_PER_PIXEL = 1.5f;
    public static final float ZOOM_MIN = 0.25f;
    public static final float ZOOM_MAX = 4f;
    /** What one notch of the mouse wheel multiplies the zoom by (and divides it by, the other way). */
    private static final float ZOOM_STEP = 1.25f;
    /** Packed light of every block: full sky and block light. */
    public static final int FULL_BRIGHT = 0xF000F0;
    /** The longest side of the shown schematic, in blocks. */
    public static final int MAX_SIDE = 256;
    /** Most blocks the shown schematic may hold. */
    public static final long MAX_BLOCKS = 200_000;
    /** Part of the view the model may fill. */
    private static final double FILL = 0.94;

    /** The smallest square (GUI pixels) worth drawing. */
    public static final int MIN_VIEW_SIZE = 40;
    /** A section of the shown model: this many cells on each edge are built together. */
    public static final int SECTION = 16;
    /** Above this many blocks a rougher copy is shown first while the full one is built. */
    public static final long ROUGH_BLOCKS = 20_000;
    /** The render thread spends at most this long per frame on turning finished pieces into display lists. */
    public static final long UPLOAD_BUDGET_MS = 3;
    /** Built previews kept for going back to a file. */
    public static final int CACHE_SIZE = 4;
    /** The building of a model gives up after this long. */
    public static final long BUILD_TIMEOUT_MS = 30_000;

    /** With the full model built, zooming goes on until a block is this many GUI pixels wide. */
    public static final float DETAIL_ZOOM_PIXELS = 8f;
    /** The full (1:1) model is only built for a schematic with at most this many blocks... */
    public static final long FULL_MAX_BLOCKS = 3_000_000;
    /** ...and stops (keeping the shrunk one) once its vertex data passes this many vertices: 28 bytes each, about 140 MB. */
    public static final long FULL_MAX_VERTICES = 5_000_000;

    private PreviewMath() {}

    /** The model to draw at any zoom: the full (1:1) one once it is built, until then the shrunk (or rough) one. */
    public static <T> T modelToDraw(T shrunk, T full, boolean fullBuilt) {
        return fullBuilt && full != null ? full : shrunk;
    }

    /** Whether the full model of a schematic with this many blocks, and this many vertices built so far, may be (built on and) kept. */
    public static boolean fullModelAllowed(long blocks, long vertices, long maxVertices) {
        return blocks <= FULL_MAX_BLOCKS && vertices <= maxVertices;
    }

    /** The automatic turn in degrees (0 to 360) after this many milliseconds of turning. */
    public static float angle(long millis) {
        return Math.floorMod(millis, TURN_MS) * 360f / TURN_MS;
    }

    /** Source blocks per shown block so that the longest side is at most {@link #MAX_SIDE}. */
    public static int stepFor(int longestSide) {
        return Math.max(1, (longestSide + MAX_SIDE - 1) / MAX_SIDE);
    }

    /** The yaw after the mouse moved this many GUI pixels sideways. */
    public static float dragYaw(float yaw, int pixels) {
        return yaw + pixels * DRAG_DEGREES_PER_PIXEL;
    }

    /** The pitch after the mouse moved this many GUI pixels down; the model can be seen from straight above to straight below. */
    public static float dragPitch(float pitch, int pixels) {
        return Math.max(-90f, Math.min(90f, pitch + pixels * DRAG_DEGREES_PER_PIXEL));
    }

    /** The zoom after a scroll in this direction (positive = in). */
    public static float zoomed(float zoom, double wheel) {
        return zoomed(zoom, wheel, ZOOM_MAX);
    }

    /** The same with another upper limit. */
    public static float zoomed(float zoom, double wheel, float max) {
        return Math.max(ZOOM_MIN, Math.min(max, wheel > 0 ? zoom * ZOOM_STEP : zoom / ZOOM_STEP));
    }

    /** How far a schematic whose blocks are this many GUI pixels wide when fitted to the view can be zoomed in: until a block is {@link #DETAIL_ZOOM_PIXELS} wide. */
    public static float maxZoom(float fittedPixelsPerBlock) {
        return fittedPixelsPerBlock <= 0 ? ZOOM_MAX : Math.max(ZOOM_MAX, DETAIL_ZOOM_PIXELS / fittedPixelsPerBlock);
    }

    /** Pixels per block so that a schematic of this size fits a view of this size at the given pitch and every turn angle. */
    public static float scaleToFit(int sizeX, int sizeY, int sizeZ, int viewWidth, int viewHeight, float pitchDegrees) {
        double diagonal = Math.sqrt((double) sizeX * sizeX + (double) sizeZ * sizeZ);
        double pitch = Math.toRadians(pitchDegrees);
        double width = diagonal;
        double height = sizeY * Math.abs(Math.cos(pitch)) + diagonal * Math.abs(Math.sin(pitch));
        if (width <= 0 || height <= 0) return 1f;
        return (float) Math.min(viewWidth * FILL / width, viewHeight * FILL / height);
    }
}
