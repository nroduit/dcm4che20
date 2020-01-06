package org.dcm4che6.img.stream;

import java.util.OptionalInt;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.data.Overlays;
import org.dcm4che6.img.data.PhotometricInterpretation;
import org.dcm4che6.img.lut.ModalityLutModule;
import org.dcm4che6.img.lut.VoiLutModule;

/**
 * @author Nicolas Roduit
 *
 */
public final class ImageDescriptor {

    private final int rows;
    private final int columns;
    private final int samples;
    private final PhotometricInterpretation photometricInterpretation;
    private final int bitsAllocated;
    private final int bitsStored;
    private final int bitsCompressed;
    private final int pixelRepresentation;
    private final String sopClassUID;
    private final String bodyPartExamined;
    private final int frames;
    private final int[] embeddedOverlays;
    private final int planarConfiguration;
    private final String presentationLUTShape;
    private final String modality;
    private final Integer pixelPaddingValue;
    private final Integer pixelPaddingRangeLimit;
    private final ModalityLutModule modalityLUT;
    private final VoiLutModule voiLUT;
    private final boolean floatPixelData;
    private final int highBit;

    public ImageDescriptor(DicomObject dcm) {
        this(dcm, -1);
    }

    public ImageDescriptor(DicomObject dcm, int bitsCompressed) {
        this.rows = dcm.getInt(Tag.Rows).orElse(0);
        this.columns = dcm.getInt(Tag.Columns).orElse(0);
        this.samples = dcm.getInt(Tag.SamplesPerPixel).orElse(0);
        this.photometricInterpretation =
            PhotometricInterpretation.fromString(dcm.getString(Tag.PhotometricInterpretation).orElse("MONOCHROME2"));
        this.bitsAllocated = dcm.getInt(Tag.BitsAllocated).orElse(8);
        this.bitsStored = dcm.getInt(Tag.BitsStored).orElse(bitsAllocated);
        this.highBit = dcm.getInt(Tag.HighBit).orElse(bitsStored - 1);
        this.bitsCompressed = bitsCompressed > 0 ? Math.min(bitsCompressed, bitsAllocated) : bitsStored;
        this.pixelRepresentation = dcm.getInt(Tag.PixelRepresentation).orElse(0);
        this.planarConfiguration = dcm.getInt(Tag.PlanarConfiguration).orElse(0);
        this.sopClassUID = dcm.getString(Tag.SOPClassUID).orElse(null);
        this.bodyPartExamined = dcm.getString(Tag.BodyPartExamined).orElse(null);
        this.frames = dcm.getInt(Tag.NumberOfFrames).orElse(1);
        this.embeddedOverlays = Overlays.getEmbeddedOverlayGroupOffsets(dcm);
        this.presentationLUTShape = dcm.getString(Tag.PresentationLUTShape).orElse(null);
        this.modality = dcm.getString(Tag.Modality).orElse(null);
        this.pixelPaddingValue = getInterValue(dcm, Tag.PixelPaddingValue);
        this.pixelPaddingRangeLimit = getInterValue(dcm, Tag.PixelPaddingRangeLimit);
        this.floatPixelData = dcm.get(Tag.FloatPixelData).isPresent() || dcm.get(Tag.DoubleFloatPixelData).isPresent();
        this.modalityLUT = new ModalityLutModule(dcm); // TODO handle PixelValueTransformationSequence
        this.voiLUT = new VoiLutModule(dcm); // TODO handle PixelValueTransformationSequence
    }

    private static Integer getInterValue(DicomObject dcm, int tag) {
        OptionalInt val = dcm.getInt(tag);
        return val.isEmpty() ? null : val.getAsInt();
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public int getSamples() {
        return samples;
    }

    public PhotometricInterpretation getPhotometricInterpretation() {
        return photometricInterpretation;
    }

    public int getBitsAllocated() {
        return bitsAllocated;
    }

    public int getBitsStored() {
        return bitsStored;
    }

    public int getBitsCompressed() {
        return bitsCompressed;
    }

    public int getPixelRepresentation() {
        return pixelRepresentation;
    }

    public int getPlanarConfiguration() {
        return planarConfiguration;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public String getBodyPartExamined() {
        return bodyPartExamined;
    }

    public int getFrames() {
        return frames;
    }

    public boolean isMultiframe() {
        return frames > 1;
    }

    public int getFrameLength() {
        return rows * columns * samples * bitsAllocated / 8;
    }

    public int getLength() {
        return getFrameLength() * frames;
    }

    public boolean isSigned() {
        return pixelRepresentation != 0;
    }

    public boolean isBanded() {
        return planarConfiguration != 0;
    }

    public int[] getEmbeddedOverlays() {
        return embeddedOverlays;
    }

    public boolean isMultiframeWithEmbeddedOverlays() {
        return embeddedOverlays.length > 0 && frames > 1;
    }

    public String getPresentationLUTShape() {
        return presentationLUTShape;
    }

    public String getModality() {
        return modality;
    }

    public Integer getPixelPaddingValue() {
        return pixelPaddingValue;
    }

    public Integer getPixelPaddingRangeLimit() {
        return pixelPaddingRangeLimit;
    }

    public ModalityLutModule getModalityLutModule() {
        return modalityLUT;
    }

    public VoiLutModule getVoiLUT() {
        return voiLUT;
    }

    public boolean isFloatPixelData() {
        return floatPixelData;
    }

    public int getHighBit() {
        return highBit;
    }

}
