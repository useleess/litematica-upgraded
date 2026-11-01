package com.useleess.litematicaupgraded.preview;

/** Turns a GUI rectangle into the pixel box of the screen (whatever the GUI scale or a screen's own scaling is), from the GL matrices. */
public final class ScissorMath {
    private ScissorMath() {}

    /** @param modelView column-major 4x4, as GL gives it; likewise {@code projection}; {@code viewport} = x, y, width, height */
    public static int[] toPixels(float[] modelView, float[] projection, int[] viewport, float x, float y, float w, float h) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float[][] corners = {{x, y}, {x + w, y}, {x, y + h}, {x + w, y + h}};
        for (float[] c : corners) {
            float[] eye = multiply(modelView, c[0], c[1], 0f, 1f);
            float[] clip = multiply(projection, eye[0], eye[1], eye[2], eye[3]);
            float px = viewport[0] + (clip[0] / clip[3] * 0.5f + 0.5f) * viewport[2];
            float py = viewport[1] + (clip[1] / clip[3] * 0.5f + 0.5f) * viewport[3];
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
        }
        int x0 = Math.round(minX), y0 = Math.round(minY);
        return new int[] {x0, y0, Math.max(0, Math.round(maxX) - x0), Math.max(0, Math.round(maxY) - y0)};
    }

    private static float[] multiply(float[] m, float x, float y, float z, float w) {
        float[] r = new float[4];
        for (int row = 0; row < 4; row++) {
            r[row] = m[row] * x + m[4 + row] * y + m[8 + row] * z + m[12 + row] * w;
        }
        return r;
    }
}
