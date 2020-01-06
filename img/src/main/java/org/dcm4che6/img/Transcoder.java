package org.dcm4che6.img;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.stream.DicomFileInputStream;
import org.dcm4che6.img.util.FileUtil;
import org.dcm4che6.img.util.StringUtil;
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
     *            the rendering parameters to apply, see <code>RenderingParams<code>
     * @throws Exception
     */
    public static void dcm2image(String srcPath, String dstPath, Format ext, DicomImageReadParam params)
        throws Exception {
        if (!StringUtil.hasText(srcPath) || !StringUtil.hasText(dstPath)) {
            throw new IllegalStateException("Path cannot be empty");
        }
        Format format = ext == null ? Format.JPEG : ext;

        try (DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi)) {
            MatOfInt map = format == Format.JPEG
                ? new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, getCompressionRatio(params)) : null;
            reader.setInput(new DicomFileInputStream(srcPath));
            int nbFrames = reader.getImageDescriptor().getFrames();
            for (int i = 0; i < nbFrames; i++) {
                PlanarImage img = reader.getPlanarImage(i, params);
                boolean applyWL = isApplyingWL(params, format, img.type());
                if (applyWL) {
                    img = ImageRendering.getDefaultRenderedImage(img, reader.getImageDescriptor(), params);
                } else {
                   img = ImageRendering.getModalatyLutImage(img, reader.getImageDescriptor(), params);
                }
                Integer index = nbFrames > 1 ? i + 1 : null;
                writeImage(img, getDestinationFile(srcPath, dstPath), format, map, index);
            }
        }
    }

    /**
     * Convert a DICOM image to another DICOM image with a specific transfer syntax
     * 
     * @param srcPath
     *            the path of the source image
     * @param dstPath
     *            the path of the destination image or the path of a directory in which the source image filename will
     *            be used
     * @param dstTsuid
     *            the transfer syntax of the destination image
     * @throws Exception
     */
    public static void dcm2dcm(String srcPath, String dstPath, String dstTsuid) throws Exception {
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

            File out = adaptFileExtension(getDestinationFile(srcPath, dstPath), ".dcm");
            List<PlanarImage> images = reader.getPlanarImages(null);
            DicomOutputData imgData = new DicomOutputData(images, dicomMetaData.getImageDescriptor(), dstTsuid);
            try (DicomOutputStream dos = new DicomOutputStream(new FileOutputStream(out))) {
                dos.writeFileMetaInformation(dicomMetaData.getDicomObject().createFileMetaInformation(dstTsuid));
                if (DicomOutputData.isNativeSyntax(dstTsuid)) {
                    imgData.writRawImageData(dos, dataSet);
                } else {
                    DicomImageWriteParam param = DicomImageWriteParam.buildDicomImageWriteParam(dstTsuid);
                    DicomOutputData.adaptTagsToImage(dataSet, images.get(0), dicomMetaData.getImageDescriptor());
                    dos.writeDataSet(dataSet);
                    imgData.writCompressedImageData(dos, param);
                }
            } catch (Exception e) {
                LOGGER.error("Transcoding image data", e);
            }
        }
    }

    private static int getCompressionRatio(DicomImageReadParam params) {
        if (params == null) {
            return 80;
        }
        return params.getJpegCompressionRatio().orElse(80);
    }

    private static boolean isApplyingWL(DicomImageReadParam params, Format format, int cvType) {
        if (params == null) {
            return true;
        }
        boolean value = params.isPreserveRawImage().orElse(true);
        if (!value) {
            if (format == Format.HDR) {
                return false; // Convert all values in double so do not apply W/L
            }
            if (cvType == CvType.CV_8U) {
                return false;
            } else if (cvType == CvType.CV_16U) {
                return !format.support16U;
            } else if (cvType == CvType.CV_16S) {
                return !format.support16S;
            } else if (cvType == CvType.CV_32F) {
                return !format.support32F;
            } else if (cvType == CvType.CV_64F) {
                return format.support64F;
            }
        }
        return value;
    }

    private static File getDestinationFile(String srcPath, String dstPath) {
        File dst = new File(dstPath);
        String baseDir;
        String filename;
        if (dst.isDirectory()) {
            baseDir = dst.getPath();
            filename = new File(srcPath).getName();
        } else {
            baseDir = dst.getParent();
            filename = dst.getName();
        }
        return new File(baseDir, filename);
    }

    private static File adaptFileExtension(File file, String ext) {
        String suffix = FileUtil.getExtension(file.getName());
        if (suffix.equals(ext)) {
            return file;
        }
        return new File(file.getPath() + ext);
    }

    private static File adaptFileIndex(File file, Integer index) {
        if (index == null) {
            return file;
        }
        String si = String.format("-%03d", index);
        String path = file.getPath();
        int i = path.lastIndexOf('.');
        if (i > 0) {
            return new File(path.substring(0, i) + si + path.substring(i));
        }
        return new File(file.getPath() + si);
    }

    private static void writeImage(PlanarImage img, File file, Format ext, MatOfInt map, Integer index) {
        File out = adaptFileExtension(file, ext.extension);
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
    }
}
