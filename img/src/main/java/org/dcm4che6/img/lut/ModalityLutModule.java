package org.dcm4che6.img.lut;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.LookupTableCV;

/**
 * @author Nicolas Roduit
 *
 */
public class ModalityLutModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModalityLutModule.class);

    private OptionalDouble rescaleSlope;
    private OptionalDouble rescaleIntercept;
    private Optional<String> rescaleType;
    private Optional<String> lutType;
    private Optional<String> lutExplanation;
    private Optional<LookupTableCV> lut;

    /**
     * Modality LUT Module
     * 
     * Note: Either a Modality LUT Sequence containing a single Item or Rescale Slope and Intercept values shall be
     * present but not both. This implementation only applies a warning in such a case.
     *
     * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.html">C.11.1 Modality LUT
     *      Module</a>
     */
    public ModalityLutModule(DicomObject dcm) {
        this.rescaleSlope = OptionalDouble.empty();
        this.rescaleIntercept = OptionalDouble.empty();
        this.rescaleType = Optional.empty();
        this.lutType = Optional.empty();
        this.lutExplanation = Optional.empty();
        this.lut = Optional.empty();
        init(Objects.requireNonNull(dcm));
    }

    private void init(DicomObject dcm) {
        String modality = DicomImageUtils.getModality(dcm);
        if (dcm.get(Tag.RescaleIntercept).isPresent() && dcm.get(Tag.RescaleSlope).isPresent()) {
            if ("MR".equals(modality) || "XA".equals(modality) || "XRF".equals(modality) || "PT".equals(modality)) {
                // IHE BIR: Windowing and Rendering 4.16.4.2.2.5.4
                LOGGER.trace("Do not apply RescaleSlope and RescaleIntercept to {}", modality);
            } else {
                this.rescaleSlope = dcm.getDouble(Tag.RescaleSlope);
                this.rescaleIntercept = dcm.getDouble(Tag.RescaleIntercept);
                this.rescaleType = dcm.getString(Tag.RescaleType);
            }
        }

        Optional<DicomElement> modSeq = dcm.get(Tag.ModalityLUTSequence);
        if (modSeq.isPresent()) {
            DicomObject dcmLut = modSeq.get().getItem(0);
            if (dcmLut != null && dcmLut.get(Tag.ModalityLUTType).isPresent()
                && dcmLut.getInts(Tag.LUTDescriptor).isPresent() && dcmLut.get(Tag.LUTData).isPresent()) {
                boolean canApplyMLUT = true;

                // See http://dicom.nema.org/medical/dicom/current/output/html/part04.html#figure_N.2-1 and
                // http://dicom.nema.org/medical/dicom/current/output/html/part03.html#sect_C.8.7.1.1.2
                if ("XA".equals(modality) || "XRF".equals(modality)) {
                    String pixRel = dcm.getString(Tag.PixelIntensityRelationship).orElse(null);
                    if (("LOG".equalsIgnoreCase(pixRel) || "DISP".equalsIgnoreCase(pixRel))) {
                        canApplyMLUT = false;
                    }
                }
                if (canApplyMLUT) {
                    this.lutType = dcmLut.getString(Tag.ModalityLUTType);
                    this.lutExplanation = dcmLut.getString(Tag.LUTExplanation);
                    this.lut = DicomImageUtils.createLut(dcmLut);
                }
            }
        }

        if (rescaleIntercept.isPresent() && lut.isPresent()) {
            LOGGER.warn(
                "Either a Modality LUT Sequence or Rescale Slope and Intercept values shall be present but not both!");
        }

        if (LOGGER.isTraceEnabled()) {
            if (lut.isPresent()) {
                if (rescaleIntercept.isPresent()) {
                    LOGGER.trace("Modality LUT Sequence shall NOT be present if Rescale Intercept is present");
                }
                if (lutType.isEmpty()) {
                    LOGGER.trace("Modality Type is required if Modality LUT Sequence is present.");
                }
            } else if (rescaleIntercept.isPresent()) {
                if (rescaleSlope.isEmpty()) {
                    LOGGER.trace("Modality Rescale Slope is required if Rescale Intercept is present.");
                }
            }
        }
    }

    public OptionalDouble getRescaleSlope() {
        return rescaleSlope;
    }

    public OptionalDouble getRescaleIntercept() {
        return rescaleIntercept;
    }

    public Optional<String> getRescaleType() {
        return rescaleType;
    }

    public Optional<String> getLutType() {
        return lutType;
    }

    public Optional<String> getLutExplanation() {
        return lutExplanation;
    }

    public Optional<LookupTableCV> getLut() {
        return lut;
    }

    public void adaptWithOverlayBitMask(int shiftHighBit) {
        // Combine to the slope value
        double rs = 1.0;
        if (rescaleSlope.isEmpty()) {
            // Set valid modality LUT values
            if (rescaleIntercept.isEmpty()) {
                rescaleIntercept = OptionalDouble.of(0.0);
            }
            if (rescaleType.isEmpty()) {
                rescaleType = Optional.of("US");
            }
        }
        // Divide pixel value by (2 ^ rightBit) => remove right bits
        rs /= 1 << shiftHighBit;
        this.rescaleSlope = OptionalDouble.of(rs);
    }

}
