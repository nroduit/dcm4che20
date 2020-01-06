package org.dcm4che6.img;

import java.awt.Rectangle;

import org.dcm4che6.data.UID;
import org.dcm4che6.img.data.TransferSyntaxType;

/**
 * @author Nicolas Roduit
 *
 */
public class DicomImageWriteParam {
    private final TransferSyntaxType type;

    /** JPEG lossless point transform (0..15, default: 0) */
    private int prediction;
    private int pointTransform;
    private int nearLosslessError;
    private int compressionQuality;
    private boolean losslessCompression;
    private Rectangle sourceRegion;

    DicomImageWriteParam(TransferSyntaxType type) {
        this.type = type;
        this.prediction = 1;
        this.pointTransform = 0;
        this.nearLosslessError = 0;
        this.compressionQuality = 80;
        this.losslessCompression = true;
        this.sourceRegion = null;
    }

    public int getPrediction() {
        return prediction;
    }

    public void setPrediction(int prediction) {
        this.prediction = prediction;
    }

    public int getPointTransform() {
        return pointTransform;
    }

    public void setPointTransform(int pointTransform) {
        this.pointTransform = pointTransform;
    }

    public int getNearLosslessError() {
        return nearLosslessError;
    }

    public void setNearLosslessError(int nearLosslessError) {
        if (nearLosslessError < 0)
            throw new IllegalArgumentException("nearLossless invalid value: " + nearLosslessError);
        this.nearLosslessError = nearLosslessError;
    }

    public int getCompressionQuality() {
        return compressionQuality;
    }

    public void setCompressionQuality(int compressionQuality) {
        this.compressionQuality = compressionQuality;
    }

    public TransferSyntaxType getType() {
        return type;
    }

    public boolean isCompressionLossless() {
        return losslessCompression;
    }

    public int getJpegMode() {
        switch (type) {
            case JPEG_BASELINE:
                return 0;
            case JPEG_EXTENDED:
                return 1;
            case JPEG_SPECTRAL:
                return 2;
            case JPEG_PROGRESSIVE:
                return 3;
            case JPEG_LOSSLESS:
                return 4;
            default:
                return 0;
        }
    }

    public void setSourceRegion(Rectangle sourceRegion) {
        this.sourceRegion = sourceRegion;
        if (sourceRegion == null) {
            return;
        }
        if (sourceRegion.x < 0 || sourceRegion.y < 0 || sourceRegion.width <= 0 || sourceRegion.height <= 0) {
            throw new IllegalArgumentException("sourceRegion has illegal values!");
        }
    }

    public Rectangle getSourceRegion() {
        return sourceRegion;
    }

    public static DicomImageWriteParam buildDicomImageWriteParam(String tsuid) {
        TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
        switch (type) {
            case NATIVE:
            case RLE:
            case JPIP:
            case MPEG:
                throw new IllegalStateException(tsuid + " is not supported for compression!");
        }
        DicomImageWriteParam param = new DicomImageWriteParam(type);
        param.losslessCompression = !TransferSyntaxType.isLossyCompression(tsuid);
        param.setNearLosslessError(param.losslessCompression ? 0 : 2);

        if (type == TransferSyntaxType.JPEG_LOSSLESS) {
            param.setPointTransform(0);
            if (UID.JPEGLosslessNonHierarchical14.equals(tsuid)) {
                param.setPrediction(6);
            } else {
                param.setPrediction(1);
            }
        }

        return param;
    }
}
