package org.dcm4che6.img.data;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    public static List<OverlayData> getOverlayData(DicomObject dcm, int activationMask) {
        return getOverlayData(dcm, activationMask, false);
    }

    private static List<OverlayData> getOverlayData(DicomObject dcm, int activationMask, boolean pr) {
        List<OverlayData> data = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int gg0000 = i << 17;
            if ((activationMask & (1 << i)) != 0 && isLayerActivate(dcm, gg0000, pr)) {
                Optional<byte[]> overData = DicomImageUtils.getByteData(dcm, Tag.OverlayData | gg0000);
                if (overData.isPresent()) {
                    int rows = dcm.getInt(Tag.OverlayRows | gg0000).orElse(0);
                    int columns = dcm.getInt(Tag.OverlayColumns | gg0000).orElse(0);
                    int imageFrameOrigin = dcm.getInt(Tag.ImageFrameOrigin | gg0000).orElse(1);
                    int framesInOverlay = dcm.getInt(Tag.NumberOfFramesInOverlay | gg0000).orElse(1);
                    int[] origin = dcm.getInts(Tag.OverlayOrigin | gg0000).orElseGet( () -> new int[]{1, 1});
                    data.add(new OverlayData(gg0000, rows, columns, imageFrameOrigin, framesInOverlay, origin, overData.get()));
                }
            }
        }
        return data.isEmpty() ? Collections.emptyList() : data;
    }

    private static boolean isLayerActivate (DicomObject dcm, int gg0000, boolean pr){
        if(pr) {
            int tagOverlayActivationLayer = Tag.OverlayActivationLayer | gg0000;
            String layerName = dcm.getString(tagOverlayActivationLayer).orElse(null);
            return layerName != null;
        }
        return true;
    }

    public static List<OverlayData> getPrOverlayData(DicomObject dcm, int activationMask) {
        return getOverlayData(dcm, activationMask, true);
    }
}
