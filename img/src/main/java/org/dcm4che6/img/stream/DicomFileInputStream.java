package org.dcm4che6.img.stream;

import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javax.imageio.ImageReadParam;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageReadParam;
import org.dcm4che6.img.DicomMetaData;
import org.dcm4che6.img.data.Overlays;
import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.lut.ModalityLutModule;
import org.dcm4che6.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

/**
 * @author Nicolas Roduit
 *
 */
public class DicomFileInputStream extends DicomInputStream implements ImageReaderDescriptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomFileInputStream.class);

    private final File file;
    private DicomMetaData metadata;

    public DicomFileInputStream(File file) throws FileNotFoundException {
        super(new FileInputStream(file));
        this.file = file;
    }

    public DicomFileInputStream(Path path) throws FileNotFoundException {
        this(path.toFile());
    }

    public DicomFileInputStream(String path) throws FileNotFoundException {
        this(new File(path));
    }

    public File getFile() {
        return file;
    }

    public DicomMetaData getMetadata() throws IOException {
        if (metadata == null) {
            this.metadata = new DicomMetaData(this);
        }
        return metadata;
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
        try {
            getMetadata();
        } catch (IOException e) {
            return null;
        }
        return metadata.getImageDescriptor();
    }

    private int getHighBit() {
        ImageDescriptor desc = getImageDescriptor();
        int bitsAllocated = desc.getBitsAllocated();
        int highBit = desc.getHighBit();
        if (highBit >= bitsAllocated) {
            highBit = desc.getBitsStored() - 1;
        }
        return highBit;
    }

    public int getDataType() {
        ImageDescriptor desc = getImageDescriptor();
        int bitsAllocated = desc.getBitsAllocated();
        int samplesPerPixel = desc.getSamples();
        boolean signed = desc.isSigned();
        int dataType =
            bitsAllocated <= 8 ? DataBuffer.TYPE_BYTE : signed ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
        if (bitsAllocated == 32 && samplesPerPixel == 1) {
            dataType = desc.isFloatPixelData() ? DataBuffer.TYPE_INT : DataBuffer.TYPE_FLOAT;
        } else if (bitsAllocated == 64 && samplesPerPixel == 1) {
            dataType = DataBuffer.TYPE_DOUBLE;
        }
        return dataType;
    }

    /**
     * 
     * For overlays encoded in Overlay Data Element (60xx,3000), Overlay Bits Allocated (60xx,0100) is always 1 and
     * Overlay Bit Position (60xx,0102) is always 0.
     * 
     * @param img
     * 
     * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/chapter_8.html">8.1.2 Overlay data
     *      encoding of related data elements</a>
     * @return the bit mask for removing the pixel overlay
     */
    public PlanarImage getImageWithoutEmbeddedOverlay(PlanarImage img) {
        ImageDescriptor desc = getImageDescriptor();
        int bitsStored = desc.getBitsStored();
        int bitsAllocated = desc.getBitsAllocated();
        int dataType = getDataType();
        if (bitsStored < bitsAllocated && dataType >= DataBuffer.TYPE_BYTE && dataType < DataBuffer.TYPE_INT
            && desc.getEmbeddedOverlays().length > 0) {
            int high = getHighBit() + 1;
            int val = (1 << high) - 1;
            if (high > bitsStored) {
                val -= (1 << (high - bitsStored)) - 1;
            }
            // Set to 0 all bits upper than highBit and if lower than high-bitsStored (=> all bits outside bitStored)
            if (high > bitsStored) {
                ModalityLutModule mLut = desc.getModalityLutModule();
                mLut.adaptWithOverlayBitMask(high - bitsStored);
            }

            // Set to 0 all bits outside bitStored
            return ImageProcessor.bitwiseAnd(img.toMat(), val);
        }
        return img;
    }



    // private byte[] extractOverlay(int gg0000, WritableRaster raster) {
    // DicomObject attrs = metadata.getDicomObject();
    //
    // if (attrs.getInt(Tag.OverlayBitsAllocated | gg0000).orElse(1) == 1)
    // return null;
    //
    // int ovlyRows = attrs.getInt(Tag.OverlayRows | gg0000).orElse(0);
    // int ovlyColumns = attrs.getInt(Tag.OverlayColumns | gg0000).orElse(0);
    // int bitPosition = attrs.getInt(Tag.OverlayBitPosition | gg0000).orElse(0);
    //
    // int mask = 1 << bitPosition;
    // int length = ovlyRows * ovlyColumns;
    //
    // byte[] ovlyData = new byte[(((length + 7) >>> 3) + 1) & (~1)];
    // if (bitPosition < desc.getBitsStored())
    // LOG.info("Ignore embedded overlay #{} from bit #{} < bits stored: {}", (gg0000 >>> 17) + 1, bitPosition,
    // desc.getBitsStored());
    // else
    // Overlays.extractFromPixeldata(raster, mask, ovlyData, 0, length);
    // return ovlyData;
    // }

    private void applyOverlay(int gg0000, WritableRaster raster, int frameIndex, ImageReadParam param, int outBits,
        byte[] ovlyData) {
        DicomObject ovlyAttrs = metadata.getDicomObject();
        int grayscaleValue = 0xffff;
        if (param instanceof DicomImageReadParam) {
            DicomImageReadParam dParam = (DicomImageReadParam) param;
            Optional<PrDicomObject> psAttrs = dParam.getPresentationState();
            if (psAttrs.isPresent()) {
               DicomObject dcm = psAttrs.get().getDicomObject();
                if (dcm.get(Tag.OverlayData | gg0000).isPresent())
                    ovlyAttrs = dcm;
                grayscaleValue = Overlays.getRecommendedDisplayGrayscaleValue(dcm, gg0000);
            } else
                grayscaleValue = dParam.getOverlayGrayscaleValue();
        }
        Overlays.applyOverlay(ovlyData != null ? 0 : frameIndex, raster, ovlyAttrs, gg0000,
            grayscaleValue >>> (16 - outBits), ovlyData);
    }

    private int[] getActiveOverlayGroupOffsets(ImageReadParam param) {
        if (param instanceof DicomImageReadParam) {
            DicomImageReadParam dParam = (DicomImageReadParam) param;
            Optional<PrDicomObject> psAttrs = dParam.getPresentationState();
            if (psAttrs.isPresent())
                return Overlays.getActiveOverlayGroupOffsets(psAttrs.get().getDicomObject());
            else
                return Overlays.getActiveOverlayGroupOffsets(metadata.getDicomObject(),
                    dParam.getOverlayActivationMask());
        }
        return Overlays.getActiveOverlayGroupOffsets(metadata.getDicomObject(), 0xffff);
    }
}
