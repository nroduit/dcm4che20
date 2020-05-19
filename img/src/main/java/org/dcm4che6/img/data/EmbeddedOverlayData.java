package org.dcm4che6.img.data;

public class EmbeddedOverlayData {
    private final int groupOffset;
    private final int bitPosition;

    public EmbeddedOverlayData(int groupOffset, int bitPosition) {
        this.groupOffset = groupOffset;
        this.bitPosition = bitPosition;
    }

    public int getBitPosition() {
        return bitPosition;
    }

    public int getGroupOffset() {
        return groupOffset;
    }
}
