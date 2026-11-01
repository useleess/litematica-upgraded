package com.useleess.litematicaupgraded.preview;

/** Where the 3D view goes in the info panel: the room left below the detail lines (and the saved picture). */
public final class PreviewLayout {
    private static final int MARGIN = 4;

    private PreviewLayout() {}

    /**
     * @param contentBottom the lowest y used by the detail lines and the saved picture
     * @return x, y, width, height of the view (all the free room below the details), or null when there is too little room
     */
    public static int[] view(int panelX, int panelY, int panelWidth, int panelHeight, int contentBottom) {
        int top = Math.max(contentBottom, panelY) + MARGIN;
        int width = panelWidth - 2 * MARGIN;
        int height = panelY + panelHeight - MARGIN - top;
        if (Math.min(width, height) < PreviewMath.MIN_VIEW_SIZE) return null;
        return new int[] {panelX + MARGIN, top, width, height};
    }
}
