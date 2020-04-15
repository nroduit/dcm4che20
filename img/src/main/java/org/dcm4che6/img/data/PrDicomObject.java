package org.dcm4che6.img.data;

import java.io.FileInputStream;
/**
 * @author Nicolas Roduit
 *
 */
import java.util.Objects;
import java.util.Optional;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageUtils;
import org.dcm4che6.img.lut.ModalityLutModule;
import org.dcm4che6.img.lut.VoiLutModule;
import org.dcm4che6.io.DicomInputStream;
import org.weasis.opencv.data.LookupTableCV;

public class PrDicomObject {
    private final DicomObject dcmPR;
    private final ModalityLutModule modalityLUT;
    private Optional<VoiLutModule> voiLUT;
    private Optional<LookupTableCV> prLut;
    private Optional<String> prLutExplanation;
    private Optional<String> prLUTShapeMode;

    public PrDicomObject(DicomObject dcmPR) {
        this.dcmPR = Objects.requireNonNull(dcmPR);
        if (!"1.2.840.10008.5.1.4.1.1.11.1".equals(dcmPR.getString(Tag.SOPClassUID).orElse(null))) {
            throw new IllegalStateException("SOPClassUID does not match to a DICOM Presentation State");
        }
        this.modalityLUT = new ModalityLutModule(dcmPR);
        this.voiLUT = buildVoiLut(dcmPR);
        this.prLut = Optional.empty();
        this.prLutExplanation = Optional.empty();
        this.prLUTShapeMode = Optional.empty();
        initPrLut();
    }

    private static Optional<VoiLutModule> buildVoiLut(DicomObject dcmPR) {
        Optional<DicomElement> softvoiseq = dcmPR.get(Tag.SoftcopyVOILUTSequence);
        DicomObject seqDcm = softvoiseq.isEmpty() ? null : softvoiseq.get().getItem(0);
        return seqDcm == null ? Optional.empty() : Optional.of(new VoiLutModule(seqDcm));
    }

    /**
     * @see <a href="http://dicom.nema.org/medical/Dicom/current/output/chtml/part03/sect_C.11.6.html">C.11.6 Softcopy
     *      Presentation LUT Module</a>
     */
    private void initPrLut() {
        Optional<DicomElement> prseq = dcmPR.get(Tag.PresentationLUTSequence);
        if (prseq.isPresent()) {
            /**
             * Presentation LUT Module is always implicitly specified to apply over the full range of output of the
             * preceding transformation, and it never selects a subset or superset of the that range (unlike the VOI
             * LUT).
             */
            DicomObject dcmLut = prseq.get().getItem(0);
            if (dcmLut != null && dcmLut.get(Tag.LUTData).isPresent()) {
                prLut = DicomImageUtils.createLut(dcmLut);
                prLutExplanation = dcmPR.getString(Tag.LUTExplanation);
            }
            prLUTShapeMode = Optional.of("IDENTITY");
        } else {
            // value: INVERSE, IDENTITY
            // INVERSE => must inverse values (same as monochrome 1)
            prLUTShapeMode = dcmPR.getString(Tag.PresentationLUTShape);
        }
    }

    public DicomObject getDicomObject() {
        return dcmPR;
    }

    public Optional<LookupTableCV> getPrLut() {
        return prLut;
    }

    public Optional<String> getPrLutExplanation() {
        return prLutExplanation;
    }

    public Optional<String> getPrLutShapeMode() {
        return prLUTShapeMode;
    }

    public ModalityLutModule getModalityLutModule() {
        return modalityLUT;
    }

    public Optional<VoiLutModule> getVoiLUT() {
        return voiLUT;
    }

    public int[] getEmbeddedOverlays() {
        return Overlays.getEmbeddedOverlayGroupOffsets(dcmPR);
    }

    public boolean hasOverlay() {
        for (int i = 0; i < 16; i++) {
            int gg0000 = i << 17;
            if ((0xffff & (1 << i)) != 0
                && dcmPR.elementStream().anyMatch(d -> d.tag() == (Tag.OverlayRows | gg0000))) {
                return true;
            }
        }
        return false;
    }

    public static PrDicomObject getPresentationState(String prPath) throws Exception {
        try (DicomInputStream dis = new DicomInputStream(new FileInputStream(prPath))) {
            return new PrDicomObject(dis.readDataSet());
        }
    }
}
