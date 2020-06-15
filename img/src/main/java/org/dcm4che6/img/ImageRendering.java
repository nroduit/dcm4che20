package org.dcm4che6.img;

import org.dcm4che6.img.data.EmbeddedOverlay;
import org.dcm4che6.img.data.OverlayData;
import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.lut.WindLevelParameters;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.opencv.core.CvType;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;
import org.weasis.opencv.op.lut.PresentationStateLut;

import java.awt.*;
import java.awt.image.DataBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Nicolas Roduit
 */
public class ImageRendering {

    private ImageRendering() {
    }

    public static PlanarImage getModalityLutImage(final PlanarImage imageSource, ImageDescriptor desc,
                                                  DicomImageReadParam params, int frameIndex) {
        PlanarImage img = getImageWithoutEmbeddedOverlay(imageSource, desc);
        DicomImageAdapter adapter = new DicomImageAdapter(img, desc);
        WindLevelParameters p = new WindLevelParameters(adapter, params);
        int datatype = img.type();

        if (datatype >= CvType.CV_8U && datatype < CvType.CV_32S) {
            LookupTableCV modalityLookup =
                    adapter.getModalityLookup(p, p.isInverseLut());
            return modalityLookup == null ? img.toImageCV() : modalityLookup.lookup(img.toMat());
        }
        return img;
    }

    public static PlanarImage getDefaultRenderedImage(final PlanarImage imageSource, ImageDescriptor desc, DicomImageReadParam params, int frameIndex) {
        PlanarImage img = getImageWithoutEmbeddedOverlay(imageSource, desc);
        img = getVoiLutImage(img, desc, params);
        return getOverlays(imageSource, img, desc, params, frameIndex);
    }

    public static PlanarImage getVoiLutImage(final PlanarImage imageSource, ImageDescriptor desc,
                                              DicomImageReadParam params) {
        DicomImageAdapter adapter = new DicomImageAdapter(imageSource, desc);
        WindLevelParameters p = new WindLevelParameters(adapter, params);
        int datatype = imageSource.type();

        if (datatype >= CvType.CV_8U && datatype < CvType.CV_32S) {
            LookupTableCV modalityLookup = adapter.getModalityLookup(p, p.isInverseLut());
            ImageCV imageModalityTransformed =
                    modalityLookup == null ? imageSource.toImageCV() : modalityLookup.lookup(imageSource.toMat());

            /*
             * C.11.2.1.2 Window center and window width
             *
             * Theses Attributes shall be used only for Images with Photometric Interpretation (0028,0004) values of
             * MONOCHROME1 and MONOCHROME2. They have no meaning for other Images.
             */
            if ((!p.isAllowWinLevelOnColorImage()
                    || MathUtil.isEqual(p.getWindow(), 255.0) && MathUtil.isEqual(p.getLevel(), 127.5))
                    && !desc.getPhotometricInterpretation().isMonochrome()) {
                /*
                 * If photometric interpretation is not monochrome do not apply VOILUT. It is necessary for
                 * PALETTE_COLOR.
                 */
                return imageModalityTransformed;
            }

            LookupTableCV voiLookup = adapter.getVOILookup(p);
            PresentationStateLut prDcm = p.getPresentationState();
            Optional<LookupTableCV> prLut = prDcm == null ? Optional.empty() : prDcm.getPrLut();
            if (prLut.isEmpty()) {
                return voiLookup.lookup(imageModalityTransformed);
            }

            ImageCV imageVoiTransformed =
                    voiLookup == null ? imageModalityTransformed : voiLookup.lookup(imageModalityTransformed);
            return prLut.get().lookup(imageVoiTransformed);

        } else if (datatype >= CvType.CV_32S) {
            double low = p.getLevel() - p.getWindow() / 2.0;
            double high = p.getLevel() + p.getWindow() / 2.0;
            double range = high - low;
            if (range < 1.0 && datatype == DataBuffer.TYPE_INT) {
                range = 1.0;
            }
            double slope = 255.0 / range;
            double yint = 255.0 - slope * high;

            return ImageProcessor.rescaleToByte(ImageCV.toMat(imageSource), slope, yint);
        }
        return null;
    }

    public static PlanarImage getOverlays(final PlanarImage imageSource, PlanarImage currentImage, ImageDescriptor desc, DicomImageReadParam params, int frameIndex) {
        Optional<PrDicomObject> prDcm = params.getPresentationState();
        List<OverlayData> overlays = new ArrayList<>();
        if(prDcm.isPresent()){
            overlays.addAll(prDcm.get().getOverlays());
        }
        List<EmbeddedOverlay> embeddedOverlays = desc.getEmbeddedOverlay();
        overlays.addAll(desc.getOverlayData());

        if (!embeddedOverlays.isEmpty() || !overlays.isEmpty()) {
            int width = currentImage.width();
            int height = currentImage.height();
            if (width == imageSource.width() && height == imageSource.height()) {
                ImageCV overlay = new ImageCV(height, width, CvType.CV_8UC1);
                byte[] pixelData = new byte[height * width];
                byte pixVal = (byte) 255;

                for (EmbeddedOverlay data : embeddedOverlays) {
                    int mask = 1 << data.getBitPosition();
                    for (int j = 0; j < height; j++) {
                        for (int i = 0; i < width; i++) {
                            double[] pix = imageSource.get(j, i);
                            if ((((int) pix[0]) & mask) != 0) {
                                pixelData[j * width + i] = pixVal;
                            }
                        }
                    }
                }

                for (OverlayData data : overlays) {
                    int imageFrameOrigin = data.getImageFrameOrigin();
                    int framesInOverlay = data.getFramesInOverlay();
                    int overlayFrameIndex = frameIndex - imageFrameOrigin + 1;
                    if (overlayFrameIndex >= 0 && overlayFrameIndex < framesInOverlay) {
                        int ovHeight = data.getRows();
                        int ovWidth = data.getColumns();
                        int ovOff = ovHeight * ovWidth * overlayFrameIndex;
                        byte[] pix = data.getData();
                        int x0 = data.getOrigin()[1] - 1;
                        int y0 = data.getOrigin()[0] - 1;
                        for (int j = y0; j < ovHeight; j++) {
                            for (int i = x0; i < ovWidth; i++) {
                                int index = ovOff + (j - y0) * ovWidth + (i - x0);
                                int b = pix[index / 8] & 0xff;
                                if ((b  & (1 << (index % 8))) != 0) {
                                    pixelData[j * width + i] = pixVal;
                                }
                            }
                        }
                    }
                }

                overlay.put(0, 0, pixelData);
                return ImageProcessor.overlay(currentImage.toMat(), overlay, params.getOverlayColor().orElse(Color.WHITE));
            }
        }
        return currentImage;
    }

    /**
     * For overlays encoded in Overlay Data Element (60xx,3000), Overlay Bits Allocated (60xx,0100) is always 1 and
     * Overlay Bit Position (60xx,0102) is always 0.
     *
     * @param img
     * @return the bit mask for removing the pixel overlay
     * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/chapter_8.html">8.1.2 Overlay data
     * encoding of related data elements</a>
     */
    public static PlanarImage getImageWithoutEmbeddedOverlay(PlanarImage img, ImageDescriptor desc) {
        Objects.requireNonNull(img);
        List<EmbeddedOverlay> embeddedOverlays = Objects.requireNonNull(desc).getEmbeddedOverlay();
        if (!embeddedOverlays.isEmpty()) {
            int bitsStored = desc.getBitsStored();
            int bitsAllocated = desc.getBitsAllocated();
            if (bitsStored < desc.getBitsAllocated() && bitsAllocated >= 8 && bitsAllocated <= 16) {
                int highBit = desc.getHighBit();
                int high = highBit + 1;
                int val = (1 << high) - 1;
                if (high > bitsStored) {
                    val -= (1 << (high - bitsStored)) - 1;
                }
                // Set to 0 all bits upper than highBit and if lower than high-bitsStored (=> all bits outside bitStored)
                if (high > bitsStored) {
                    desc.getModalityLUT().adaptWithOverlayBitMask(high - bitsStored);
                }

                // Set to 0 all bits outside bitStored
                return ImageProcessor.bitwiseAnd(img.toMat(), val);
            }
        }
        return img;
    }
}
