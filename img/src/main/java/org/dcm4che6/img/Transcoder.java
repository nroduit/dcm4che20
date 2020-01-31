package org.dcm4che6.img;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.stream.DicomFileInputStream;
import org.dcm4che6.img.util.FileUtil;
import org.dcm4che6.io.DicomOutputStream;
import org.opencv.core.CvType;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

/**
 * @author Nicolas Roduit
 *
 */
public class Transcoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(Transcoder.class);

    public enum Format {
        JPEG(".jpg", false, false, false, false), PNG(".png", true, false, false, false),
        TIF(".tif", true, false, true, true), PNM(".pnm", true, false, false, false),
        BMP(".bmp", false, false, false, false), HDR(".hdr", false, false, false, true);

        final String extension;
        final boolean support16U;
        final boolean support16S;
        final boolean support32F;
        final boolean support64F;

        Format(String ext, boolean support16U, boolean support16S, boolean support32F, boolean support64F) {
            this.extension = ext;
            this.support16U = support16U;
            this.support16S = support16S;
            this.support32F = support32F;
            this.support64F = support64F;
        }
    }

    public static final DicomImageReaderSpi dicomImageReaderSpi = new DicomImageReaderSpi();

    /**
     * Convert a DICOM image to a standard image with some rendering parameters
     * 
     * @param srcPath
     *            the path of the source image
     * @param dstPath
     *            the path of the destination image or the path of a directory in which the source image filename will
     *            be used
     * @param ext
     *            the destination image format
     * @param params
     *            the standard image conversion parameters
     * @return
     * @throws Exception
     */
    public static List<File> dcm2image(String srcPath, String dstPath, ImageTranscodeParam params) throws Exception {
        List<File> outFiles = new ArrayList<>();
        Format format = params.getFormat();
        try (DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi)) {
            MatOfInt map = format == Format.JPEG
                ? new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, getCompressionRatio(params)) : null;
            File inFile = new File(srcPath);
            reader.setInput(new DicomFileInputStream(inFile));
            int nbFrames = reader.getImageDescriptor().getFrames();
            for (int i = 0; i < nbFrames; i++) {
                PlanarImage img = reader.getPlanarImage(i, params.getReadParam());
                boolean rawImg = isPreserveRawImage(params, format, img.type());
                if (rawImg) {
                    img = ImageRendering.getModalityLutImage(img, reader.getImageDescriptor(), params.getReadParam());
                } else {
                    img =
                        ImageRendering.getDefaultRenderedImage(img, reader.getImageDescriptor(), params.getReadParam());
                }
                Integer index = nbFrames > 1 ? i + 1 : null;
                File outFile = writeImage(img, FileUtil.getOutputFile(inFile, new File(dstPath)), format, map, index);
                outFiles.add(outFile);
            }
        }
        return outFiles;
    }

    /**
     * Convert a DICOM image to another DICOM image with a specific transfer syntax
     * 
     * @param srcPath
     *            the path of the source image
     * @param dstPath
     *            the path of the destination image or the path of a directory in which the source image filename will
     *            be used
     * @param params
     *            the DICOM conversion parameters
     * @throws Exception
     */
    public static File dcm2dcm(String srcPath, String dstPath, DicomTranscodeParam params) throws Exception {
        File outFile = null;
        try (DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi)) {
            File inFile = new File(srcPath);
            reader.setInput(new DicomFileInputStream(inFile));

            DicomMetaData dicomMetaData = reader.getStreamMetadata();
            DicomObject dataSet = DicomObject.newDicomObject();
            for (DicomElement el : dicomMetaData.getDicomObject()) {
                if (el.tag() == Tag.PixelData) {
                    break;
                }
                dataSet.add(el);
            }

            outFile = adaptFileExtension(FileUtil.getOutputFile(inFile, new File(dstPath)), ".dcm", ".dcm");
            List<PlanarImage> images = reader.getPlanarImages(params.getReadParam());
            String dstTsuid = params.getOutputTsuid();
            DicomOutputData imgData = new DicomOutputData(images, dicomMetaData.getImageDescriptor(), dstTsuid);
            try (DicomOutputStream dos = new DicomOutputStream(new FileOutputStream(outFile))) {
                dos.writeFileMetaInformation(dicomMetaData.getDicomObject().createFileMetaInformation(dstTsuid));
                if (DicomOutputData.isNativeSyntax(dstTsuid)) {
                    imgData.writRawImageData(dos, dataSet);
                } else {
                    int[] jpegWriteParams = DicomOutputData.adaptTagsToImage(dataSet, images.get(0), dicomMetaData.getImageDescriptor(),
                        params.getWriteJpegParam());
                    dos.writeDataSet(dataSet);
                    imgData.writCompressedImageData(dos, jpegWriteParams);
                }
            } catch (Exception e) {
                LOGGER.error("Transcoding image data", e);
            }
        }
        return outFile;
    }

    private static int getCompressionRatio(ImageTranscodeParam params) {
        if (params == null) {
            return 80;
        }
        return params.getJpegCompressionQuality().orElse(80);
    }

    private static boolean isPreserveRawImage(ImageTranscodeParam params, Format format, int cvType) {
        if (params == null) {
            return false;
        }
        boolean value = params.isPreserveRawImage().orElse(false);
        if (value) {
            if (format == Format.HDR || cvType == CvType.CV_8U) {
                return true; // Convert all values in double so do not apply W/L
            } else if (cvType == CvType.CV_16U) {
                return format.support16U;
            } else if (cvType == CvType.CV_16S) {
                return format.support16S;
            } else if (cvType == CvType.CV_32F) {
                return format.support32F;
            } else if (cvType == CvType.CV_64F) {
                return format.support64F;
            }
        }
        return value;
    }

    private static File adaptFileExtension(File file, String inExt, String outExt) {
        String suffix = FileUtil.getExtension(file.getName());
        if (suffix.equals(outExt)) {
            return file;
        }
        String name = file.getName();
        if (name.endsWith(inExt)) {
            return new File(file.getParent(), name.substring(0, name.length() - inExt.length()) + outExt);
        }
        return new File(file.getPath() + outExt);
    }

    private static File adaptFileIndex(File file, Integer index) {
        if (index == null) {
            return file;
        }
        String si = String.format("-%03d", index);
        String name = file.getName();
        int i = name.lastIndexOf('.');
        if (i > 0) {
            return new File(file.getParent(), name.substring(0, i) + si + name.substring(i));
        }
        return new File(file.getPath() + si);
    }

    private static File writeImage(PlanarImage img, File file, Format ext, MatOfInt map, Integer index) {
        File out = adaptFileExtension(file, ".dcm", ext.extension);
        out = adaptFileIndex(out, index);
        if (map == null) {
            if (!ImageProcessor.writeImage(img.toMat(), out)) {
                LOGGER.error("Cannot Transform to {} {}", ext, img);
                FileUtil.delete(out);
            }
        } else {
            if (!ImageProcessor.writeImage(img.toMat(), out, map)) {
                LOGGER.error("Cannot Transform to {} {}", ext, img);
                FileUtil.delete(out);
            }
        }
        return out;
    }
}
