package org.dcm4che6.img.data;

public class OverlayData {
    private final int groupOffset;
    private final int rows;
    private final int columns;
    private final int imageFrameOrigin;
    private final int framesInOverlay;
    private final int[] origin;
    private final byte[] data;

    public OverlayData(int groupOffset, int rows, int columns, int imageFrameOrigin, int framesInOverlay, int[] origin, byte[] data) {
        this.groupOffset = groupOffset;
        this.rows = rows;
        this.columns = columns;
        this.imageFrameOrigin = imageFrameOrigin;
        this.framesInOverlay = framesInOverlay;
        this.origin = origin;
        this.data = data;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public int getGroupOffset() {
        return groupOffset;
    }

    public int getImageFrameOrigin() {
        return imageFrameOrigin;
    }

    public int getFramesInOverlay() {
        return framesInOverlay;
    }

    public int[] getOrigin() {
        return origin;
    }

    public byte[] getData() {
        return data;
    }
}
