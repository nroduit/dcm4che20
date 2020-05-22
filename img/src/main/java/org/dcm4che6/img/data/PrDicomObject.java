package org.dcm4che6.img.data;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageUtils;
import org.dcm4che6.img.lut.ModalityLutModule;
import org.dcm4che6.img.lut.VoiLutModule;
import org.dcm4che6.io.DicomInputStream;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.PresentationStateLut;

import java.io.FileInputStream;
import java.io.IOException;
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
    private final Optional<VoiLutModule> voiLUT;
    private final Optional<LookupTableCV> prLut;
    private final Optional<String> prLutExplanation;
    private final Optional<String> prLUTShapeMode;

    public PrDicomObject(DicomObject dcmPR) {
        this.dcmPR = Objects.requireNonNull(dcmPR);
        if (!"1.2.840.10008.5.1.4.1.1.11.1".equals(dcmPR.getString(Tag.SOPClassUID).orElse(null))) {
            throw new IllegalStateException("SOPClassUID does not match to a DICOM Presentation State");
        }
        this.modalityLUT = new ModalityLutModule(dcmPR);
        this.overlays = OverlayData.getPrOverlayData(dcmPR, -1);
        this.voiLUT = buildVoiLut(dcmPR);

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
        Optional<DicomElement> softVoiSeq = dcmPR.get(Tag.SoftcopyVOILUTSequence);
        DicomObject seqDcm = softVoiSeq.isEmpty() ? null : softVoiSeq.get().getItem(0);
        return seqDcm == null ? Optional.empty() : Optional.of(new VoiLutModule(seqDcm));
    }

    public DicomObject getDicomObject() {
        return dcmPR;
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

    public boolean hasOverlay() {
        return !overlays.isEmpty();
    }

    public static PrDicomObject getPresentationState(String prPath) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(new FileInputStream(prPath))) {
            return new PrDicomObject(dis.readDataSet());
        }
    }
}
