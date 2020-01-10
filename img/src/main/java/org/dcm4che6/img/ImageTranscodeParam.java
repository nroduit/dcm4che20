package org.dcm4che6.img;

import java.util.Optional;
import java.util.OptionalInt;

import org.dcm4che6.img.Transcoder.Format;
import org.dcm4che6.img.util.LangUtil;

public class ImageTranscodeParam {
    private final DicomImageReadParam readParam;
    private final Format format;

    private Integer jpegCompressionQuality;
    private Boolean preserveRawImage;

    public ImageTranscodeParam(Format format) {
        this(null, format);
    }

    public ImageTranscodeParam(DicomImageReadParam readParam, Format format) {
        this.readParam = readParam == null ? new DicomImageReadParam() : readParam;
        this.format = format == null ? Format.JPEG : format;
        this.preserveRawImage = null;
        this.jpegCompressionQuality = null;
    }

    public DicomImageReadParam getReadParam() {
        return readParam;
    }

    public OptionalInt getJpegCompressionQuality() {
        return  LangUtil.getOptionalInteger(jpegCompressionQuality);
    }

    /**
     * @param jpegCompressionQuality
     *            between 1 to 100 (100 is the best lossy quality).
     */
    public void setJpegCompressionQuality(int jpegCompressionQuality) {
        this.jpegCompressionQuality = jpegCompressionQuality;
    }

    public Optional<Boolean> isPreserveRawImage() {
        return Optional.ofNullable(preserveRawImage);
    }

    /**
     * It preserves the raw data without applying the window/level values. The default value is TRUE. False will apply
     * W/L and the output image will be always a 8-bit per sample image.
     * 
     * @param preserveRawImage
     */
    public void setPreserveRawImage(Boolean preserveRawImage) {
        this.preserveRawImage = preserveRawImage;
    }

    public Format getFormat() {
        return format;
    }

}
