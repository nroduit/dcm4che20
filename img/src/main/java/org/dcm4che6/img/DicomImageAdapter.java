package org.dcm4che6.img;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

import org.dcm4che6.data.Tag;
import org.dcm4che6.img.data.PhotometricInterpretation;
import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.lut.LutParameters;
import org.dcm4che6.img.lut.LutShape;
import org.dcm4che6.img.lut.PresetWindowLevel;
import org.dcm4che6.img.lut.WindLevelParameters;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.img.util.MathUtil;
import org.dcm4che6.img.util.SoftHashMap;
import org.opencv.core.Core.MinMaxLocResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

/**
 * @author Nicolas Roduit
 *
 */
public class DicomImageAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomImageAdapter.class);

    private static final SoftHashMap<LutParameters, LookupTableCV> LUT_Cache = new SoftHashMap<>();

    private final PlanarImage image;
    private final ImageDescriptor desc;
    private int bitsStored;

    private MinMaxLocResult minMax = null;
    private List<PresetWindowLevel> windowingPresetCollection = null;

    public DicomImageAdapter(PlanarImage image, ImageDescriptor desc) {
        this.image = Objects.requireNonNull(image);
        this.desc = Objects.requireNonNull(desc);
        this.bitsStored = desc.getBitsStored();
        findMinMaxValues(true);

    }

    public int getBitsStored() {
        return bitsStored;
    }

    public void setBitsStored(int bitsStored) {
        this.bitsStored = bitsStored;
    }

    public MinMaxLocResult getMinMax() {
        return minMax;
    }

    public void setMinMax(MinMaxLocResult minMax) {
        this.minMax = minMax;
    }

    public PlanarImage getImage() {
        return image;
    }

    public ImageDescriptor getImageDescriptor() {
        return desc;
    }

    public MinMaxLocResult findRawMinMaxValues(boolean exclude8bitImage) throws OutOfMemoryError {
        // This function can be called several times from the inner class Load.
        // Do not compute min and max it has already be done
        if (minMax == null) {
            MinMaxLocResult val;
            if (ImageConversion.convertToDataType(image.type()) == DataBuffer.TYPE_BYTE && exclude8bitImage) {
                val = new MinMaxLocResult();
                val.minVal = 0.0;
                val.maxVal = 255.0;
            } else {
                val = ImageProcessor.findMinMaxValues(image.toMat());
                if (val == null) {
                    val = new MinMaxLocResult();
                }
                // Handle special case when min and max are equal, ex. black image
                // + 1 to max enables to display the correct value
                if (val.minVal == val.maxVal) {
                    val.maxVal += 1.0;
                }
            }
            this.minMax = val;
        }
        return minMax;
    }

    protected void resetMinmax() {
        this.minMax = null;
    }

    protected void findMinMaxValues(boolean exclude8bitImage) {
        /*
         * This function can be called several times from the inner class Load. min and max will be computed only once.
         */

        if (minMax == null) {
            // Cannot trust SmallestImagePixelValue and LargestImagePixelValue values! So search min and max values
            int bitsAllocated = desc.getBitsAllocated();

            boolean monochrome = desc.getPhotometricInterpretation().isMonochrome();
            if (monochrome) {
                Integer paddingValue = desc.getPixelPaddingValue();
                if (paddingValue != null) {
                    Integer paddingLimit = desc.getPixelPaddingRangeLimit();
                    Integer paddingValueMin =
                        (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
                    Integer paddingValueMax =
                        (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);
                    findMinMaxValues(paddingValueMin, paddingValueMax);
                }
            }

            if (minMax == null) {
                findRawMinMaxValues(!monochrome);
            }

            if (bitsStored < bitsAllocated && minMax != null) {
                boolean isSigned = desc.isSigned();
                int minInValue = isSigned ? -(1 << (bitsStored - 1)) : 0;
                int maxInValue = isSigned ? (1 << (bitsStored - 1)) - 1 : (1 << bitsStored) - 1;
                if (minMax.minVal < minInValue || minMax.maxVal > maxInValue) {
                    /*
                     *
                     *
                     * When the image contains values outside the bits stored values, the bits stored is replaced by the
                     * bits allocated for having a LUT which handles all the values.
                     *
                     * Overlays in pixel data should be masked before finding min and max.
                     */
                    this.bitsStored = bitsAllocated;
                }
            }
            /*
             * Lazily compute image pixel transformation here since inner class Load is called from a separate and
             * dedicated worker Thread. Also, it will be computed only once
             *
             * Considering that the default pixel padding option is true and Inverse LUT action is false
             */
            getModalityLookup(true, false, null);
        }
    }

    /**
     * Computes Min/Max values from Image excluding range of values provided
     *
     * @param paddingValueMin
     * @param paddingValueMax
     */
    private void findMinMaxValues(Integer paddingValueMin, Integer paddingValueMax) {
        MinMaxLocResult val;
        if (ImageConversion.convertToDataType(image.type()) == DataBuffer.TYPE_BYTE) {
            val = new MinMaxLocResult();
            val.minVal = 0.0;
            val.maxVal = 255.0;
        } else {
            val = ImageProcessor.findMinMaxValues(image.toMat(), paddingValueMin, paddingValueMax);
            // Handle special case when min and max are equal, ex. black image
            // + 1 to max enables to display the correct value
            if (val != null && val.minVal == val.maxVal) {
                val.maxVal += 1.0;
            }
        }
        this.minMax = val;
    }

    public int getMinAllocatedValue(boolean pixelPadding, PrDicomObject pr) {
        boolean signed = isModalityLutOutSigned(pixelPadding, pr);
        int bitsAllocated = desc.getBitsAllocated();
        int maxValue = signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
        return signed ? -(maxValue + 1) : 0;
    }

    public int getMaxAllocatedValue(boolean pixelPadding, PrDicomObject pr) {
        boolean signed = isModalityLutOutSigned(pixelPadding, pr);
        int bitsAllocated = desc.getBitsAllocated();
        return signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
    }

    /**
     * In the case where Rescale Slope and Rescale Intercept are used for modality pixel transformation, the output
     * ranges may be signed even if Pixel Representation is unsigned.
     *
     * @param pixelPadding
     *
     * @return
     */
    public boolean isModalityLutOutSigned(boolean pixelPadding, PrDicomObject pr) {
        boolean signed = desc.isSigned();
        return getMinValue(pixelPadding, pr) < 0 || signed;
    }

    /**
     * @return return the min value after modality pixel transformation and after pixel padding operation if padding
     *         exists.
     */
    public double getMinValue(boolean pixelPadding, PrDicomObject pr) {
        return minMaxValue(pixelPadding, true, pr);
    }

    /**
     * @return return the max value after modality pixel transformation and after pixel padding operation if padding
     *         exists.
     */
    public double getMaxValue(boolean pixelPadding, PrDicomObject pr) {
        return minMaxValue(pixelPadding, false, pr);
    }

    private double minMaxValue(boolean pixelPadding, boolean minVal, PrDicomObject pr) {
        MinMaxLocResult minmax = findRawMinMaxValues(!desc.getPhotometricInterpretation().isMonochrome());
        Number min = pixelToRealValue(minmax.minVal, pixelPadding, pr);
        Number max = pixelToRealValue(minmax.maxVal, pixelPadding, pr);
        if (min == null || max == null) {
            return 0;
        }
        // Computes min and max as slope can be negative
        if (minVal) {
            return Math.min(min.doubleValue(), max.doubleValue());
        }
        return Math.max(min.doubleValue(), max.doubleValue());
    }

    public double getRescaleIntercept(PrDicomObject dcm) {
        if (dcm != null) {
            OptionalDouble prIntercept = dcm.getModalityLutModule().getRescaleIntercept();
            if (prIntercept.isPresent()) {
                return prIntercept.getAsDouble();
            }
        }
        return desc.getModalityLutModule().getRescaleIntercept().orElse(0.0);
    }

    public double getRescaleSlope(PrDicomObject dcm) {
        if (dcm != null) {
            OptionalDouble prSlope = dcm.getModalityLutModule().getRescaleSlope();
            if (prSlope.isPresent()) {
                return prSlope.getAsDouble();
            }
        }
        return desc.getModalityLutModule().getRescaleSlope().orElse(1.0);
    }

    public double getFullDynamicWidth(boolean pixelPadding, PrDicomObject pr) {
        return getMaxValue(pixelPadding, pr) - getMinValue(pixelPadding, pr);
    }

    public double getFullDynamicCenter(boolean pixelPadding, PrDicomObject pr) {
        double minValue = getMinValue(pixelPadding, pr);
        double maxValue = getMaxValue(pixelPadding, pr);
        return minValue + (maxValue - minValue) / 2.f;
    }

    /**
     * @return default as first element of preset List <br>
     *         Note : null should never be returned since auto is at least one preset
     */
    public PresetWindowLevel getDefaultPreset(boolean pixelPadding, PrDicomObject pr) {
        List<PresetWindowLevel> presetList = getPresetList(pixelPadding, pr);
        return (presetList != null && !presetList.isEmpty()) ? presetList.get(0) : null;
    }

    public synchronized List<PresetWindowLevel> getPresetList(boolean pixelPadding, PrDicomObject pr) {
        if (windowingPresetCollection == null && minMax != null) {
            windowingPresetCollection = PresetWindowLevel.getPresetCollection(this, pixelPadding, "[DICOM]", pr);
        }
        return windowingPresetCollection;
    }

    public LutShape getDefaultShape(boolean pixelPadding, PrDicomObject pr) {
        PresetWindowLevel defaultPreset = getDefaultPreset(pixelPadding, pr);
        return (defaultPreset != null) ? defaultPreset.getLutShape() : LutShape.LINEAR;
    }

    public double getDefaultWindow(boolean pixelPadding, PrDicomObject pr) {
        PresetWindowLevel defaultPreset = getDefaultPreset(pixelPadding, pr);
        return (defaultPreset != null) ? defaultPreset.getWindow()
            : minMax == null ? 0.0 : minMax.maxVal - minMax.minVal;
    }

    public double getDefaultLevel(boolean pixelPadding, PrDicomObject pr) {
        PresetWindowLevel defaultPreset = getDefaultPreset(pixelPadding, pr);
        if (defaultPreset != null) {
            return defaultPreset.getLevel();
        }
        if (minMax != null) {
            return minMax.minVal + (minMax.maxVal - minMax.minVal) / 2.0;
        }
        return 0.0f;
    }

    public Number pixelToRealValue(Number pixelValue, boolean pixelPadding, PrDicomObject pr) {
        if (pixelValue != null) {
            LookupTableCV lookup = getModalityLookup(pixelPadding, false, pr);
            if (lookup != null) {
                int val = pixelValue.intValue();
                if (val >= lookup.getOffset() && val < lookup.getOffset() + lookup.getNumEntries()) {
                    return lookup.lookup(0, val);
                }
            }
        }
        return pixelValue;
    }

    /**
     * DICOM PS 3.3 $C.11.1 Modality LUT Module
     *
     */
    protected LookupTableCV getModalityLookup(boolean pixelPadding, boolean inverseLUTAction, PrDicomObject pr) {
        Integer paddingValue = desc.getPixelPaddingValue();
        LookupTableCV prModLut = (pr != null ? pr.getModalityLutModule().getLut().orElse(null) : null);
        final LookupTableCV mLUTSeq = prModLut == null ? desc.getModalityLutModule().getLut().orElse(null) : prModLut;
        if (mLUTSeq != null) {
            if (!pixelPadding || paddingValue == null) {
                MinMaxLocResult minmax = findRawMinMaxValues(!desc.getPhotometricInterpretation().isMonochrome());
                if (minmax.minVal >= mLUTSeq.getOffset()
                    && minmax.maxVal < mLUTSeq.getOffset() + mLUTSeq.getNumEntries()) {
                    return mLUTSeq;
                } else if (prModLut == null) {
                    LOGGER.warn(
                        "Pixel values doesn't match to Modality LUT sequence table. So the Modality LUT is not applied.");
                }
            } else {
                LOGGER.warn("Cannot apply Modality LUT sequence and Pixel Padding");
            }
        }

        boolean inverseLut = isPhotometricInterpretationInverse(pr);
        if (pixelPadding) {
            inverseLut ^= inverseLUTAction;
        }
        LutParameters lutparams = getLutParameters(pixelPadding, mLUTSeq, inverseLut, pr);
        // Not required to have a modality lookup table
        if (lutparams == null) {
            return null;
        }
        LookupTableCV modalityLookup = LUT_Cache.get(lutparams);

        if (modalityLookup != null) {
            return modalityLookup;
        }

        if (mLUTSeq != null) {
            if (mLUTSeq.getNumBands() == 1) {
                if (mLUTSeq.getDataType() == DataBuffer.TYPE_BYTE) {
                    byte[] data = mLUTSeq.getByteData(0);
                    if (data != null) {
                        modalityLookup = new LookupTableCV(data, mLUTSeq.getOffset(0));
                    }
                } else {
                    short[] data = mLUTSeq.getShortData(0);
                    if (data != null) {
                        modalityLookup = new LookupTableCV(data, mLUTSeq.getOffset(0),
                            mLUTSeq.getData() instanceof DataBufferUShort);
                    }
                }
            }
            if (modalityLookup == null) {
                modalityLookup = mLUTSeq;
            }
        } else {
            modalityLookup = DicomImageUtils.createRescaleRampLut(lutparams);
        }

        if (desc.getPhotometricInterpretation().isMonochrome()) {
            DicomImageUtils.applyPixelPaddingToModalityLUT(modalityLookup, lutparams);
        }
        LUT_Cache.put(lutparams, modalityLookup);
        return modalityLookup;
    }

    public boolean isPhotometricInterpretationInverse(PrDicomObject dcm) {
        String prLUTShape = dcm == null ? null : dcm.getDicomObject().getString(Tag.PresentationLUTShape).orElse(null);
        if (prLUTShape == null) {
            prLUTShape = desc.getPresentationLUTShape();
        }
        return prLUTShape != null ? "INVERSE".equals(prLUTShape)
            : PhotometricInterpretation.MONOCHROME1 == desc.getPhotometricInterpretation();
    }

    public LutParameters getLutParameters(boolean pixelPadding, LookupTableCV mLUTSeq, boolean inversePaddingMLUT,
        PrDicomObject pr) {
        Integer paddingValue = desc.getPixelPaddingValue();

        boolean isSigned = desc.isSigned();
        double intercept = getRescaleIntercept(pr);
        double slope = getRescaleSlope(pr);

        // No need to have a modality lookup table
        if (bitsStored > 16
            || (MathUtil.isEqual(slope, 1.0) && MathUtil.isEqualToZero(intercept) && paddingValue == null)) {
            return null;
        }

        Integer paddingLimit = desc.getPixelPaddingRangeLimit();
        boolean outputSigned = false;
        int bitsOutputLut;
        if (mLUTSeq == null) {
            MinMaxLocResult minmax = findRawMinMaxValues(!desc.getPhotometricInterpretation().isMonochrome());
            double minValue = minmax.minVal * slope + intercept;
            double maxValue = minmax.maxVal * slope + intercept;
            bitsOutputLut = Integer.SIZE - Integer.numberOfLeadingZeros((int) Math.round(maxValue - minValue));
            outputSigned = minValue < 0 || isSigned;
            if (outputSigned && bitsOutputLut <= 8) {
                // Allows to handle negative values with 8-bit image
                bitsOutputLut = 9;
            }
        } else {
            bitsOutputLut = mLUTSeq.getDataType() == DataBuffer.TYPE_BYTE ? 8 : 16;
        }
        return new LutParameters(intercept, slope, pixelPadding, paddingValue, paddingLimit, bitsStored, isSigned,
            outputSigned, bitsOutputLut, inversePaddingMLUT);

    }

    /**
     *
     * @return 8 bits unsigned Lookup Table
     */

    public LookupTableCV getVOILookup(WindLevelParameters p) {
        if (p.getLutShape() == null) {
            return null;
        }

        int minValue;
        int maxValue;
        PrDicomObject pr = p.getPresentationState();
        /*
         * When pixel padding is activated, VOI LUT must extend to the min bit stored value when MONOCHROME2 and to the
         * max bit stored value when MONOCHROME1. See C.7.5.1.1.2
         */
        if (p.isFillOutsideLutRange()
            || (desc.getPixelPaddingValue() != null && desc.getPhotometricInterpretation().isMonochrome())) {
            boolean pixelPadding = p.isPixelPadding();
            minValue = getMinAllocatedValue(pixelPadding, pr);
            maxValue = getMaxAllocatedValue(pixelPadding, pr);
        } else {
            minValue = (int) p.getLevelMin();
            maxValue = (int) p.getLevelMax();
        }

        return DicomImageUtils.createVoiLut(p.getLutShape(), p.getWindow(), p.getLevel(), minValue, maxValue, 8, false,
            isPhotometricInterpretationInverse(pr));
    }
}
