package org.dcm4che6.img.data;

import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Optional;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageUtils;
import org.dcm4che6.img.util.FileUtil;
import org.dcm4che6.util.TagUtils;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.op.ImageConversion;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class Overlays {

    private static final Logger LOG = LoggerFactory.getLogger(Overlays.class);

    public static int[] getActiveOverlayGroupOffsets(DicomObject psdcm) {
        return getOverlayGroupOffsets(psdcm, Tag.OverlayActivationLayer, -1);
    }

    public static int[] getActiveOverlayGroupOffsets(DicomObject dcm,
            int activationMask) {
        return getOverlayGroupOffsets(dcm, Tag.OverlayRows, activationMask);
    }

    public static int[] getOverlayGroupOffsets(DicomObject dcm, int tag,
            int activationMask) {
        int len = 0;
        int[] result = new int[16];
        for (int i = 0; i < result.length; i++) {
            int gg0000 = i << 17;
            if ((activationMask & (1<<i)) != 0
                    &&  dcm.elementStream().anyMatch(d -> d.tag() == (tag | gg0000)))
                result[len++] = gg0000;
        }
        return Arrays.copyOf(result, len);
    }

    public static int[] getEmbeddedOverlayGroupOffsets(DicomObject dcm) {
        int len = 0;
        int[] result = new int[16];
        int bitsAllocated = dcm.getInt(Tag.BitsAllocated).orElse(8);
        int bitsStored = dcm.getInt(Tag.BitsStored).orElse(bitsAllocated);
        for (int i = 0; i < result.length; i++) {
            int gg0000 = i << 17;
            if (dcm.getInt(Tag.OverlayBitsAllocated | gg0000).orElse(1) != 1) {
                int ovlyBitPosition = dcm.getInt(Tag.OverlayBitPosition | gg0000, 0).orElse(0);
                if (ovlyBitPosition < bitsStored)
                    LOG.info("Ignore embedded overlay #{} from bit #{} < bits stored: {}",
                            (gg0000 >>> 17) + 1, ovlyBitPosition, bitsStored);
                else
                    result[len++] = gg0000;
            }
        }
        return Arrays.copyOf(result, len);
    }
    
    public byte[][] extractEmbeddedOverlay(Mat img, DicomObject dcm) {
        // Serialize overlay (from pixel data)
        int[] embeddedOverlayGroupOffsets = Overlays.getEmbeddedOverlayGroupOffsets(dcm);
        if (embeddedOverlayGroupOffsets.length > 0) {
            ObjectOutput objOut = null;
            try {
                byte[][] overlayData = new byte[embeddedOverlayGroupOffsets.length][];
                for (int i = 0; i < embeddedOverlayGroupOffsets.length; i++) {
                    overlayData[i] = extractOverlay(embeddedOverlayGroupOffsets[i],
                        ImageConversion.toBufferedImage(img).getRaster(), dcm);
                }
                return overlayData;
            } catch (Exception e) {
                LOG.error("Cannot serialize overlay", e); //$NON-NLS-1$
            } finally {
                FileUtil.safeClose(objOut);
            }
        }
        return null;
    }
    

    public static byte[] extractOverlay(int gg0000, Raster raster, DicomObject dcm) {
        if (dcm.getInt(Tag.OverlayBitsAllocated | gg0000).orElse(1) == 1) {
            return null;
        }

        int ovlyRows = dcm.getInt(Tag.OverlayRows | gg0000).orElse(0);
        int ovlyColumns = dcm.getInt(Tag.OverlayColumns | gg0000).orElse(0);
        int bitPosition = dcm.getInt(Tag.OverlayBitPosition | gg0000).orElse(0);

        int mask = 1 << bitPosition;
        int length = ovlyRows * ovlyColumns;

        // Binary size = ((imageSize + 7) / 8 ) + 1 & (-2) = 32769 & (-2) = 1000000000000001 & 1111111111111111110
        byte[] ovlyData = new byte[(((length + 7) >>> 3) + 1) & (~1)];
        extractFromPixeldata(raster, mask, ovlyData, 0, length);
        return ovlyData;
    }

   public static void extractFromPixeldata(Raster raster, int mask, 
            byte[] ovlyData, int off, int length) {
        ComponentSampleModel sm = (ComponentSampleModel) raster.getSampleModel();
        int rows = raster.getHeight();
        int columns = raster.getWidth();
        int stride = sm.getScanlineStride();
        DataBuffer db = raster.getDataBuffer();
        switch (db.getDataType()) {
        case DataBuffer.TYPE_BYTE:
            extractFromPixeldata(((DataBufferByte) db).getData(),
                    rows, columns, stride, mask,
                    ovlyData, off, length);
            break;
        case DataBuffer.TYPE_USHORT:
            extractFromPixeldata(((DataBufferUShort) db).getData(),
                    rows, columns, stride, mask,
                    ovlyData, off, length);
            break;
        case DataBuffer.TYPE_SHORT:
            extractFromPixeldata(((DataBufferShort) db).getData(),
                    rows, columns, stride, mask,
                    ovlyData, off, length);
            break;
        default:
            throw new UnsupportedOperationException(
                    "Unsupported DataBuffer type: " + db.getDataType());
        }
    }

    private static void extractFromPixeldata(byte[] pixeldata,
            int rows, int columns, int stride, int mask,
            byte[] ovlyData, int off, int length) {
        for (int y = 0, i = off, imax = off + length;
                y < columns && i < imax; y++) {
            for (int j = y * stride, jmax = j + rows; j < jmax && i < imax; j++, i++) {
                if ((pixeldata[j] & mask) != 0)
                    ovlyData[i >>> 3] |= 1 << (i & 7);
            }
        }
    }

    private static void extractFromPixeldata(short[] pixeldata,
            int rows, int columns, int stride, int mask,
            byte[] ovlyData, int off, int length) {
        for (int y = 0, i = off, imax = off + length;
                y < rows && i < imax; y++) {
            for (int j = y * stride, jmax = j + columns; j < jmax && i < imax; j++, i++) {
                if ((pixeldata[j] & mask) != 0) {
                    ovlyData[i >>> 3] |= 1 << (i & 7);
                }
            }
        }
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
        if(first.isPresent()) {
            return first.get().getInt(Tag.RecommendedDisplayGrayscaleValue).orElse(-1);
        }
        
        throw new IllegalArgumentException("No Graphic Layer: " + layerName);
    }

    public static void applyOverlay(int frameIndex, WritableRaster raster,
        DicomObject dcm, int gg0000, int pixelValue, byte[] ovlyData) {

        int imageFrameOrigin = dcm.getInt(Tag.ImageFrameOrigin | gg0000).orElse(1);
        int framesInOverlay = dcm.getInt(Tag.NumberOfFramesInOverlay | gg0000).orElse(1);
        int ovlyFrameIndex = frameIndex - imageFrameOrigin  + 1;
        if (ovlyFrameIndex < 0 || ovlyFrameIndex >= framesInOverlay)
            return;
        
        int tagOverlayRows = Tag.OverlayRows | gg0000;
        int tagOverlayColumns = Tag.OverlayColumns | gg0000;
        int tagOverlayData = Tag.OverlayData | gg0000;
        int tagOverlayOrigin = Tag.OverlayOrigin | gg0000;

        int ovlyRows = dcm.getInt(tagOverlayRows).orElse(-1);
        int ovlyColumns = dcm.getInt(tagOverlayColumns).orElse(-1);
        int[] ovlyOrigin = dcm.getInts(tagOverlayOrigin).orElseThrow(() ->new IllegalArgumentException("Missing "
                    + TagUtils.toString(tagOverlayOrigin)
                    + " Overlay Origin"));
        if (ovlyData == null) {
            Optional<byte[]> overData = DicomImageUtils.getByteData(dcm, tagOverlayData);
            if(overData.isPresent())
                ovlyData = overData.get();
        }

        if (ovlyData == null)
            throw new IllegalArgumentException("Missing "
                    + TagUtils.toString(tagOverlayData)
                    + " Overlay Data");
        if (ovlyRows <= 0)
            throw new IllegalArgumentException(
                    TagUtils.toString(tagOverlayRows)
                    + " Overlay Rows [" + ovlyRows + "]");
        if (ovlyColumns <= 0)
            throw new IllegalArgumentException(
                    TagUtils.toString(tagOverlayColumns)
                    + " Overlay Columns [" + ovlyColumns + "]");
        if (ovlyOrigin.length != 2)
            throw new IllegalArgumentException(
                    TagUtils.toString(tagOverlayOrigin)
                    + " Overlay Origin " + Arrays.toString(ovlyOrigin));

        int x0 = ovlyOrigin[1] - 1;
        int y0 = ovlyOrigin[0] - 1;

        int ovlyLen = ovlyRows * ovlyColumns;
        int ovlyOff = ovlyLen * ovlyFrameIndex;
        for (int i = ovlyOff >>> 3,
               end = (ovlyOff + ovlyLen + 7) >>> 3; i < end; i++) {
            int ovlyBits = ovlyData[i] & 0xff;
            for (int j = 0; (ovlyBits>>>j) != 0; j++) {
                if ((ovlyBits & (1<<j)) == 0)
                    continue;

                int ovlyIndex = ((i<<3) + j) - ovlyOff;
                if (ovlyIndex >= ovlyLen)
                    continue;

                int y = y0 + ovlyIndex / ovlyColumns;
                int x = x0 + ovlyIndex % ovlyColumns;
                try {
                    raster.setSample(x, y, 0, pixelValue);
                } catch (ArrayIndexOutOfBoundsException ignore) {}
            }
        }
    }

}
