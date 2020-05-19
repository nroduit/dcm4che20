package org.dcm4che6.img.data;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageUtils;
import org.dcm4che6.util.TagUtils;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.op.ImageConversion;

import java.awt.image.*;
import java.io.ObjectOutput;
import java.util.*;

/**
 * @author Nicolas Roduit
 */
public class Overlays {

    private static final Logger LOG = LoggerFactory.getLogger(Overlays.class);

    public static List<OverlayData> getOverlayGroupOffsets(DicomObject dcm, int tag,
                                               int activationMask) {
        List<OverlayData> data = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int gg0000 = i << 17;
            if ((activationMask & (1 << i)) != 0) {
                Optional<byte[]> overData = DicomImageUtils.getByteData(dcm, Tag.OverlayData | gg0000);
                if (overData.isPresent()){
                    int rows = dcm.getInt(Tag.OverlayRows | gg0000).orElse(0);
                    int columns = dcm.getInt(Tag.OverlayColumns | gg0000).orElse(0);
                    int imageFrameOrigin = dcm.getInt(Tag.ImageFrameOrigin | gg0000).orElse(1);
                    int framesInOverlay = dcm.getInt(Tag.NumberOfFramesInOverlay | gg0000).orElse(1);
                    int[] origin = dcm.getInts(Tag.OverlayOrigin | gg0000).orElse(new int[]{1,1});
                        data.add(new OverlayData(gg0000, rows, columns, imageFrameOrigin, framesInOverlay, origin, overData.get()));
                    }
            }
        }
        return data.isEmpty() ? Collections.emptyList() : data;
    }


    public static List<EmbeddedOverlayData> getEmbeddedOverlay(DicomObject dcm) {
        List<EmbeddedOverlayData> data = new ArrayList<>();
        int bitsAllocated = dcm.getInt(Tag.BitsAllocated).orElse(8);
        int bitsStored = dcm.getInt(Tag.BitsStored).orElse(bitsAllocated);
        for (int i = 0; i < 16; i++) {
            int gg0000 = i << 17;
            if (dcm.getInt(Tag.OverlayBitsAllocated | gg0000).orElse(1) != 1) {
                int bitPosition = dcm.getInt(Tag.OverlayBitPosition | gg0000).orElse(0);
                if (bitPosition < bitsStored) {
                    LOG.info("Ignore embedded overlay #{} from bit #{} < bits stored: {}",
                            (gg0000 >>> 17) + 1, bitPosition, bitsStored);
                }
                else {
                    data.add(new EmbeddedOverlayData(gg0000, bitPosition));
                }
            }
        }
        return data.isEmpty() ? Collections.emptyList() : data;
    }

    public static int getRecommendedDisplayGrayscaleValue(DicomObject psdcm,
                                                          int gg0000) {
        int tagOverlayActivationLayer = Tag.OverlayActivationLayer | gg0000;
        String layerName = psdcm.getString(tagOverlayActivationLayer).orElseThrow(() -> new IllegalArgumentException("Missing "
                + TagUtils.toString(tagOverlayActivationLayer)
                + " Overlay Activation Layer"));

        DicomElement layers = psdcm.get(Tag.GraphicLayerSequence).orElseThrow(() -> new IllegalArgumentException("Missing "
                + TagUtils.toString(Tag.GraphicLayerSequence)
                + " Graphic Layer Sequence"));
        Optional<DicomObject> first = layers.itemStream().filter(d -> layerName.equals(d.getString(Tag.GraphicLayer).orElse(null))).findFirst();
        if (first.isPresent()) {
            return first.get().getInt(Tag.RecommendedDisplayGrayscaleValue).orElse(-1);
        }

        throw new IllegalArgumentException("No Graphic Layer: " + layerName);
    }
}
