package org.dcm4che6.img.data;

import org.dcm4che6.data.UID;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public enum PhotometricInterpretation {
    MONOCHROME1(true, true, false, false) ,
    MONOCHROME2(true, false, false, false) ,
    PALETTE_COLOR(false, false, false, false) {
        @Override
        public String toString() {
            return "PALETTE COLOR";
        }
    },
    RGB(false, false, false, false) {

        @Override
        public PhotometricInterpretation compress(String tsuid) {
            switch (tsuid) {
                case UID.JPEGBaseline1:
                case UID.JPEGExtended24:
                    return YBR_FULL_422;
                case UID.JPEGSpectralSelectionNonHierarchical68Retired:
                case UID.JPEGFullProgressionNonHierarchical1012Retired:
                    return YBR_FULL;
                case UID.JPEG2000LosslessOnly:
                case UID.JPEG2000Part2MultiComponentLosslessOnly:
                    return YBR_RCT;
                case UID.JPEG2000:
                case UID.JPEG2000Part2MultiComponent:
                    return YBR_ICT;
            }
            return this;
        }
    },
    YBR_FULL(false, false, true, false),
    YBR_FULL_422(false, false, true, true) {
        @Override
        public int frameLength(int w, int h, int samples, int bitsAllocated) {
            return ColorSubsampling.YBR_XXX_422.frameLength(w, h);
        }
    },
    YBR_ICT(false, false, true, false),
    
    YBR_PARTIAL_420(false, false, true, true) {
        @Override
        public int frameLength(int w, int h, int samples, int bitsAllocated) {
            return ColorSubsampling.YBR_XXX_420.frameLength(w, h);
        }
    },
    YBR_PARTIAL_422(false, false, true, true) {
        @Override
        public int frameLength(int w, int h, int samples, int bitsAllocated) {
            return ColorSubsampling.YBR_XXX_422.frameLength(w, h);
        }

    },
    YBR_RCT(false, false, true, false);

    private final boolean monochrome;
    private final boolean inverse;
    private final boolean ybr;
    private final boolean subSampled;

    PhotometricInterpretation(boolean monochrome, boolean inverse, boolean ybr, boolean subSampled) {
        this.monochrome = monochrome;
        this.inverse = inverse;
        this.ybr = ybr;
        this.subSampled = subSampled;
    }

    public static PhotometricInterpretation fromString(String s) {
        return s.equals("PALETTE COLOR") ? PALETTE_COLOR : valueOf(s);
    }

    public int frameLength(int w, int h, int samples, int bitsAllocated) {
        return w * h * samples * bitsAllocated / 8;
    }

    public boolean isMonochrome() {
        return monochrome;
    }

    public boolean isYBR() {
        return ybr;
    }

    public PhotometricInterpretation compress(String tsuid) {
        return this;
    }

    public boolean isInverse() {
        return inverse;
    }

    public boolean isSubSampled() {
        return subSampled;
    }
}
