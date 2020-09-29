package org.dcm4che6.img.data;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageUtils;
import org.dcm4che6.img.lut.ModalityLutModule;
import org.dcm4che6.img.lut.VoiLutModule;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.img.util.DicomObjectUtil;
import org.dcm4che6.io.DicomInputStream;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.PresentationStateLut;

import java.awt.Color;
import java.awt.geom.Area;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Nicolas Roduit
 */
public class PrDicomObject implements PresentationStateLut {
    private final DicomObject dcmPR;
    private final ModalityLutModule modalityLUT;
    private final List<OverlayData> overlays;
    private final List<OverlayData> shutterOverlays;
    private final Optional<VoiLutModule> voiLUT;
    private final Optional<LookupTableCV> prLut;
    private final Optional<String> prLutExplanation;
    private final Optional<String> prLUTShapeMode;

    public PrDicomObject(DicomObject dcmPR) {
        this(dcmPR, null);
    }

    public PrDicomObject(DicomObject dcmPR, ImageDescriptor desc) {
        this.dcmPR = Objects.requireNonNull(dcmPR);
        // TODO handle sopclassUID http://dicom.nema.org/medical/dicom/current/output/chtml/part04/sect_B.5.html
        // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_A.33.2.3.html
        // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_A.33.3.3.html
        // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_A.33.4.3.html
        // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_A.33.6.3.html
        if (!dcmPR.getString(Tag.SOPClassUID).orElse("").startsWith("1.2.840.10008.5.1.4.1.1.11.")) {
            throw new IllegalStateException("SOPClassUID does not match to a DICOM Presentation State");
        }
        this.modalityLUT = desc == null ? new ModalityLutModule(dcmPR) : desc.getModalityLUT();
        this.voiLUT = buildVoiLut(dcmPR);
        this.overlays = OverlayData.getPrOverlayData(dcmPR, -1);
        this.shutterOverlays = desc == null ? OverlayData.getOverlayData(dcmPR, 0xffff) : desc.getOverlayData();
        // Implement graphics http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_A.33.2.3.html
        // Implement mask module http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.13.html

        Optional<DicomElement> prSeq = dcmPR.get(Tag.PresentationLUTSequence);
        if (prSeq.isPresent()) {
            /**
             * @see <a href="http://dicom.nema.org/medical/Dicom/current/output/chtml/part03/sect_C.11.6.html">C.11.6 Softcopy Presentation LUT Module</a>
             *
             * Presentation LUT Module is always implicitly specified to apply over the full range of output of the
             * preceding transformation, and it never selects a subset or superset of the that range (unlike the VOI
             * LUT).
             */
            DicomObject dcmLut = prSeq.get().getItem(0);
            if (dcmLut != null && dcmLut.get(Tag.LUTData).isPresent()) {
                this.prLut = DicomImageUtils.createLut(dcmLut);
                this.prLutExplanation = dcmPR.getString(Tag.LUTExplanation);
            }
            else {
                this.prLut = Optional.empty();
                this.prLutExplanation = Optional.empty();
            }
            this.prLUTShapeMode = Optional.of("IDENTITY");
        } else {
            // value: INVERSE, IDENTITY
            // INVERSE => must inverse values (same as monochrome 1)
            this.prLUTShapeMode = dcmPR.getString(Tag.PresentationLUTShape);
            this.prLut = Optional.empty();
            this.prLutExplanation = Optional.empty();
        }
    }

    private static Optional<VoiLutModule> buildVoiLut(DicomObject dcmPR) {
        DicomObject seqDcm = DicomObjectUtil.getNestedDataset(dcmPR, Tag.SoftcopyVOILUTSequence );
        return seqDcm == null ? Optional.empty() : Optional.of(new VoiLutModule(seqDcm));
    }

    public DicomObject getDicomObject() {
        return dcmPR;
    }

    public LocalDateTime getPresentationCreationDateTime() {
        return DicomObjectUtil.dateTime(dcmPR, Tag.PresentationCreationDate , Tag.PresentationCreationTime);
    }

    @Override
    public Optional<LookupTableCV> getPrLut() {
        return prLut;
    }

    @Override
    public Optional<String> getPrLutExplanation() {
        return prLutExplanation;
    }

    @Override
    public Optional<String> getPrLutShapeMode() {
        return prLUTShapeMode;
    }

    public ModalityLutModule getModalityLutModule() {
        return modalityLUT;
    }

    public Optional<VoiLutModule> getVoiLUT() {
        return voiLUT;
    }

    public List<OverlayData> getOverlays() {
        return overlays;
    }

    public List<OverlayData> getShutterOverlays() {
        return shutterOverlays;
    }

    public String getPrContentLabel() {
        return dcmPR.getString(Tag.ContentLabel).orElse("PR " + dcmPR.getInt(Tag.InstanceNumber).orElse(0));
    }

    public boolean hasOverlay() {
        return !overlays.isEmpty();
    }

    public List<DicomObject> getReferencedSeriesSequence() {
        return DicomObjectUtil.getSequence(dcmPR, Tag.ReferencedSeriesSequence);
    }

    public List<DicomObject> getGraphicAnnotationSequence() {
        return DicomObjectUtil.getSequence(dcmPR, Tag.GraphicAnnotationSequence);
    }

    public List<DicomObject> getGraphicLayerSequence() {
        return DicomObjectUtil.getSequence(dcmPR, Tag.GraphicLayerSequence);
    }


    public Area getShutterShape() {
        return DicomObjectUtil.getShutterShape(dcmPR);
    }

    public Color getShutterColor() {
        return DicomObjectUtil.getShutterColor(dcmPR);
    }

    public static PrDicomObject getPresentationState(String prPath) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(new FileInputStream(prPath))) {
            return new PrDicomObject(dis.readDataSet());
        }
    }

    public boolean isImageFrameApplicable(String seriesInstanceUID, String sopInstanceUID, int frame) {
        return isImageFrameApplicable(Tag.ReferencedFrameNumber, seriesInstanceUID, sopInstanceUID, frame);
    }

    public boolean isSegmentationSegmentApplicable(String seriesInstanceUID, String sopInstanceUID, int segment) {
        return isImageFrameApplicable(Tag.ReferencedSegmentNumber, seriesInstanceUID, sopInstanceUID, segment);
    }

    private boolean isImageFrameApplicable(int childTag, String seriesInstanceUID, String sopInstanceUID, int frame) {
        if (StringUtil.hasText(seriesInstanceUID)) {
            for (DicomObject refSeriesSeq : getReferencedSeriesSequence()) {
                if (seriesInstanceUID.equals(refSeriesSeq.getString(Tag.SeriesInstanceUID).orElse(null))) {
                    List<DicomObject> refImgSeq = DicomObjectUtil.getSequence(Objects.requireNonNull(refSeriesSeq), Tag.ReferencedImageSequence);
                    return DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(refImgSeq, childTag, sopInstanceUID, frame, true);
                }
            }
        }
        return false;
    }



}
