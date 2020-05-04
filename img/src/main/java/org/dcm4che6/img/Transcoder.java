package org.dcm4che6.img;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.stream.BytesWithImageDescriptor;
import org.dcm4che6.img.stream.DicomFileInputStream;
import org.dcm4che6.io.DicomOutputStream;
import org.opencv.core.CvType;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
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
     * @param params
     *            the standard image conversion parameters
     * @return
     * @throws Exception
     */
    public static List<Path> dcm2image(Path srcPath, Path dstPath, ImageTranscodeParam params) throws Exception {
        List<Path> outFiles = new ArrayList<>();
        Format format = params.getFormat();
        try (DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi)) {
            MatOfInt map = format == Format.JPEG
                ? new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, getCompressionRatio(params)) : null;
            reader.setInput(new DicomFileInputStream(srcPath));
            int nbFrames = reader.getImageDescriptor().getFrames();
            int indexSize = (int) Math.log10(nbFrames);
            indexSize = nbFrames > 1 ? indexSize + 1 : 0;
            for (int i = 0; i < nbFrames; i++) {
                PlanarImage img = reader.getPlanarImage(i, params.getReadParam());
                boolean rawImg = isPreserveRawImage(params, format, img.type());
                if (rawImg) {
                    img = ImageRendering.getModalityLutImage(img, reader.getImageDescriptor(), params.getReadParam());
                } else {
                    img =
                        ImageRendering.getDefaultRenderedImage(img, reader.getImageDescriptor(), params.getReadParam());
                }
                Path outPath = writeImage(img, FileUtil.getOutputPath(srcPath, dstPath), format, map, i + 1, indexSize);
                outFiles.add(outPath);
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
    public static Path dcm2dcm(Path srcPath, Path dstPath, DicomTranscodeParam params) throws Exception {
        Path outPath = null;
        try (DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi)) {
            reader.setInput(new DicomFileInputStream(srcPath));

            DicomMetaData dicomMetaData = reader.getStreamMetadata();
            DicomObject dataSet = DicomObject.newDicomObject();
            for (DicomElement el : dicomMetaData.getDicomObject()) {
                if (el.tag() == Tag.PixelData) {
                    break;
                }
                dataSet.add(el);
            }

            outPath = adaptFileExtension(FileUtil.getOutputPath(srcPath, dstPath), ".dcm", ".dcm");
            List<PlanarImage> images = reader.getPlanarImages(params.getReadParam());
            String dstTsuid = params.getOutputTsuid();
            DicomOutputData imgData = new DicomOutputData(images, dicomMetaData.getImageDescriptor(), dstTsuid);
            try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(outPath))) {
                dos.writeFileMetaInformation(dicomMetaData.getDicomObject().createFileMetaInformation(dstTsuid));
                if (DicomOutputData.isNativeSyntax(dstTsuid)) {
                    imgData.writRawImageData(dos, dataSet);
                } else {
                    int[] jpegWriteParams = DicomOutputData.adaptTagsToImage(dataSet, images.get(0),
                        dicomMetaData.getImageDescriptor(), params.getWriteJpegParam());
                    dos.writeDataSet(dataSet);
                    imgData.writCompressedImageData(dos, jpegWriteParams);
                }
            } catch (Exception e) {
                LOGGER.error("Transcoding image data", e);
            }
        }
        return outPath;
    }

    public static DicomOutputData dcm2dcm(BytesWithImageDescriptor desc, DicomTranscodeParam params) throws Exception {
        try (DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi)) {
            reader.setInput(desc);

            List<PlanarImage> images = reader.getPlanarImages(params.getReadParam());
            String dstTsuid = params.getOutputTsuid();
            return new DicomOutputData(images, desc.getImageDescriptor(), dstTsuid);
        }
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

    private static Path adaptFileExtension(Path path, String inExt, String outExt) {
        String fname = path.getFileName().toString();
        String suffix = FileUtil.getExtension(fname);
        if (suffix.equals(outExt)) {
            return path;
        }
        if (suffix.endsWith(inExt)) {
            return Path.of(path.getParent().toString(), fname.substring(0, fname.length() - inExt.length()) + outExt);
        }
        return path.resolveSibling(fname + outExt);
    }

    private static Path adaptFileIndex(Path path, Integer index) {
        if (index == null) {
            return path;
        }

        String insert = String.format("$1-%03d$2", index);
        return path.resolveSibling(path.getFileName().toString().replaceFirst("(.*?)(\\.[^.]+)?$", insert));
    }

    private static Path writeImage(PlanarImage img, Path path, Format ext, MatOfInt map, int index, int indexSize) {
        Path outPath = adaptFileExtension(path, ".dcm", ext.extension);
        outPath = FileUtil.addFileIndex(outPath, index, indexSize);
        if (map == null) {
            if (!ImageProcessor.writeImage(img.toMat(), outPath.toFile())) {
                LOGGER.error("Cannot Transform to {} {}", ext, img);
                FileUtil.delete(outPath);
            }
        } else {
            if (!ImageProcessor.writeImage(img.toMat(), outPath.toFile(), map)) {
                LOGGER.error("Cannot Transform to {} {}", ext, img);
                FileUtil.delete(outPath);
            }
        }
        return outPath;
    }
}
