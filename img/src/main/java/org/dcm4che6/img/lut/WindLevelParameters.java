package org.dcm4che6.img.lut;

import java.util.Objects;

import org.dcm4che6.img.DicomImageAdapter;
import org.dcm4che6.img.DicomImageReadParam;
import org.dcm4che6.img.data.PrDicomObject;
import org.weasis.opencv.op.lut.LutShape;

/**
 * @author Nicolas Roduit
 *
 */
public class WindLevelParameters {

    private final double window;
    private final double level;
    private final double levelMin;
    private final double levelMax;
    private final boolean pixelPadding;
    private final boolean inverseLut;
    private final boolean fillOutsideLutRange;
    private final boolean allowWinLevelOnColorImage;
    private final LutShape lutShape;
    private final PrDicomObject dcmPR;

    public WindLevelParameters(DicomImageAdapter adapter) {
        this(adapter, null);
    }

    public WindLevelParameters(DicomImageAdapter adapter, DicomImageReadParam params) {
        Objects.requireNonNull(adapter);
        if (params == null) {
            this.dcmPR = null;
            this.fillOutsideLutRange = false;
            this.allowWinLevelOnColorImage = false;
            this.pixelPadding = true;
            this.inverseLut = false;
            this.window = adapter.getDefaultWindow(pixelPadding, null);
            this.level = adapter.getDefaultLevel(pixelPadding, null);
            this.lutShape = adapter.getDefaultShape(pixelPadding, null);
            this.levelMin = Math.min(level - window / 2.0, adapter.getMinValue(pixelPadding, null));
            this.levelMax = Math.max(level + window / 2.0, adapter.getMaxValue(pixelPadding, null));
        } else {
            this.dcmPR = params.getPresentationState().orElse(null);
            this.fillOutsideLutRange = params.getFillOutsideLutRange().orElse(false);
            this.allowWinLevelOnColorImage = params.getApplyWindowLevelToColorImage().orElse(false);
            this.pixelPadding = params.getApplyPixelPadding().orElse(true);
            this.inverseLut = params.getInverseLut().orElse(false);
            this.window = params.getWindowWidth().orElseGet(() -> adapter.getDefaultWindow(pixelPadding, dcmPR));
            this.level = params.getWindowCenter().orElseGet(() -> adapter.getDefaultLevel(pixelPadding, dcmPR));
            this.lutShape = params.getVoiLutShape().orElseGet(() -> adapter.getDefaultShape(pixelPadding, dcmPR));
            this.levelMin = Math.min(params.getLevelMin().orElseGet(() -> level - window / 2.0),
                adapter.getMinValue(pixelPadding, dcmPR));
            this.levelMax = Math.max(params.getLevelMax().orElseGet(() -> level + window / 2.0),
                adapter.getMaxValue(pixelPadding, dcmPR));
        }
    }

    public double getWindow() {
        return window;
    }

    public double getLevel() {
        return level;
    }

    public double getLevelMin() {
        return levelMin;
    }

    public double getLevelMax() {
        return levelMax;
    }

    public boolean isPixelPadding() {
        return pixelPadding;
    }

    public boolean isInverseLut() {
        return inverseLut;
    }

    public boolean isFillOutsideLutRange() {
        return fillOutsideLutRange;
    }

    public boolean isAllowWinLevelOnColorImage() {
        return allowWinLevelOnColorImage;
    }

    public LutShape getLutShape() {
        return lutShape;
    }

    public PrDicomObject getPresentationState() {
        return dcmPR;
    }
}
