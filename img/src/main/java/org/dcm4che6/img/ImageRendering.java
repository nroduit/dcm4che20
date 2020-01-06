package org.dcm4che6.img;

import java.awt.image.DataBuffer;
import java.util.Optional;

import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.lut.WindLevelParameters;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.img.util.MathUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

/**
 * @author Nicolas Roduit
 *
 */
public class ImageRendering {

    private ImageRendering() {
    }

    public static PlanarImage getModalatyLutImage(final PlanarImage imageSource, ImageDescriptor desc,
        DicomImageReadParam params) {
        if (imageSource == null) {
            return null;
        }
        DicomImageAdapter adapter = new DicomImageAdapter(imageSource, desc);
        WindLevelParameters p = new WindLevelParameters(adapter, params);
        int datatype = ImageConversion.convertToDataType(imageSource.type());
        boolean pixPadding = p.isPixelPadding();

        if (datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT) {
            LookupTableCV modalityLookup =
                adapter.getModalityLookup(pixPadding, p.isInverseLut(), p.getPresentationState());
            return modalityLookup == null ? imageSource.toImageCV() : modalityLookup.lookup(imageSource.toMat());
        }
        return imageSource;
    }

    public static PlanarImage getDefaultRenderedImage(final PlanarImage imageSource, ImageDescriptor desc,
        DicomImageReadParam params) {
        if (imageSource == null) {
            return null;
        }
        DicomImageAdapter adapter = new DicomImageAdapter(imageSource, desc);
        WindLevelParameters p = new WindLevelParameters(adapter, params);
        int datatype = ImageConversion.convertToDataType(imageSource.type());
        boolean pixPadding = p.isPixelPadding();

        if (datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT) {
            LookupTableCV modalityLookup =
                adapter.getModalityLookup(pixPadding, p.isInverseLut(), p.getPresentationState());
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
            PrDicomObject prDcm = p.getPresentationState();
            Optional<LookupTableCV> prLut = prDcm == null ? Optional.empty() : prDcm.getPrLut();
            if (prLut.isEmpty()) {
                return voiLookup.lookup(imageModalityTransformed);
            }

            ImageCV imageVoiTransformed =
                voiLookup == null ? imageModalityTransformed : voiLookup.lookup(imageModalityTransformed);
            return prLut.get().lookup(imageVoiTransformed);

        } else if (datatype == DataBuffer.TYPE_INT || datatype == DataBuffer.TYPE_FLOAT
            || datatype == DataBuffer.TYPE_DOUBLE) {
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

}
