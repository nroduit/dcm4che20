package org.dcm4che6.img;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.data.VR;
import org.dcm4che6.img.data.PhotometricInterpretation;
import org.dcm4che6.img.data.TransferSyntaxType;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.io.DicomOutputStream;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.PlanarImage;



/**
 * @author Nicolas Roduit
 *
 */
public class DicomOutputData {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomOutputData.class);

    private final List<PlanarImage> images;
    private final ImageDescriptor desc;
    private final String tsuid;

    public DicomOutputData(List<PlanarImage> images, ImageDescriptor desc, String tsuid) {
        this.images = Objects.requireNonNull(images);
        this.desc = Objects.requireNonNull(desc);
        this.tsuid = Objects.requireNonNull(tsuid);
        if (!isSupportedSyntax(tsuid)) {
            throw new IllegalStateException(tsuid + " is not supported as encoding syntax!");
        }
    }

    public DicomOutputData(PlanarImage image, ImageDescriptor desc, String tsuid) {
        this(List.of(image), desc, tsuid);
    }

    public void writCompressedImageData(DicomOutputStream dos, DicomJpegWriteParam param) throws IOException {
        dos.writeHeader(Tag.PixelData, VR.OB, -1);
        dos.writeHeader(Tag.Item, VR.NONE, 0);

        Mat buf = null;
        MatOfInt dicomParams = null;
        try {
            Mat mat = images.get(0).toMat();
            int cvType = mat.type();
            int elemSize = (int) mat.elemSize1();
            int channels = CvType.channels(cvType);
            int dcmFlags =
                CvType.depth(cvType) == CvType.CV_16S ? Imgcodecs.DICOM_IMREAD_SIGNED : Imgcodecs.DICOM_IMREAD_UNSIGNED;

            int epi = channels == 1 ? Imgcodecs.EPI_Monochrome2 : Imgcodecs.EPI_RGB;
            TransferSyntaxType type = param.getType();
            int compressType = Imgcodecs.DICOM_CP_JPG;
            if (type == TransferSyntaxType.JPEG_2000) {
                compressType = Imgcodecs.DICOM_CP_J2K;
            } else if (type == TransferSyntaxType.JPEG_LS) {
                compressType = Imgcodecs.DICOM_CP_JPLS;
            }

            int[] params = new int[15];
            params[Imgcodecs.DICOM_PARAM_IMREAD] = Imgcodecs.IMREAD_UNCHANGED; // Image flags
            params[Imgcodecs.DICOM_PARAM_DCM_IMREAD] = dcmFlags; // DICOM flags
            params[Imgcodecs.DICOM_PARAM_WIDTH] = mat.width(); // Image width
            params[Imgcodecs.DICOM_PARAM_HEIGHT] = mat.height(); // Image height
            params[Imgcodecs.DICOM_PARAM_COMPRESSION] = compressType; // Type of compression
            params[Imgcodecs.DICOM_PARAM_COMPONENTS] = channels; // Number of components
            params[Imgcodecs.DICOM_PARAM_BITS_PER_SAMPLE] = desc.getBitsCompressed(); // Bits per sample
            params[Imgcodecs.DICOM_PARAM_INTERLEAVE_MODE] = Imgcodecs.ILV_SAMPLE; // Interleave mode
            params[Imgcodecs.DICOM_PARAM_BYTES_PER_LINE] = mat.width() * elemSize; // Bytes per line
            params[Imgcodecs.DICOM_PARAM_ALLOWED_LOSSY_ERROR] = param.getNearLosslessError(); // Allowed lossy error
                                                                                              // for jpeg-ls
            params[Imgcodecs.DICOM_PARAM_COLOR_MODEL] = epi; // Photometric interpretation
            params[Imgcodecs.DICOM_PARAM_JPEG_MODE] = param.getJpegMode(); // JPEG Codec mode
            params[Imgcodecs.DICOM_PARAM_JPEG_QUALITY] = param.getCompressionQuality(); // JPEG lossy quality
            params[Imgcodecs.DICOM_PARAM_JPEG_PREDICTION] = param.getPrediction(); // JPEG lossless prediction
            params[Imgcodecs.DICOM_PARAM_JPEG_PT_TRANSFORM] = param.getPointTransform(); // JPEG lossless
                                                                                         // transformation point
            dicomParams = new MatOfInt(params);

            for (PlanarImage image : images) {
                buf = Imgcodecs.dicomJpgWrite(DicomImageUtils.bgr2rgb(image).toMat(), dicomParams, "");
                if (buf.empty()) {
                    throw new IOException("Native encoding error: null image");
                }

                byte[] bSrcData = new byte[buf.width() * buf.height() * (int) buf.elemSize()];
                buf.get(0, 0, bSrcData);
                dos.writeHeader(Tag.Item, VR.NONE, bSrcData.length);
                dos.write(bSrcData);
            }
            dos.writeHeader(Tag.SequenceDelimitationItem, VR.NONE, 0);
        } catch (Throwable t) {
            throw new IOException("Native encoding error", t);
        } finally {
            DicomImageReader.closeMat(dicomParams);
            DicomImageReader.closeMat(buf);
        }
    }

    protected void writRawImageData(DicomOutputStream dos, DicomObject data) {

        // Issue store the initial colormodel?
        // FileRawImage raw = null;
        // try {
        // raw = new FileRawImage(File.createTempFile("raw_", ".wcv"));
        // try (FileOutputStream df = new FileOutputStream(raw.getFile(), true); FileChannel dfc = df.getChannel()) {
        // for (int i = 0; i < images.size(); i++) {
        // FileRawImage fimg = new FileRawImage(File.createTempFile("raw_", ".wcv"));
        // try {
        // if (!fimg.write(DicomImageUtils.bgr2rgb(images.get(i)))) {
        // raw = null;
        // break;
        // } else {
        // try (FileInputStream sf = new FileInputStream(fimg.getFile());
        // FileChannel sfc = sf.getChannel()) {
        // sfc.position(FileRawImage.HEADER_LENGTH);
        // dfc.transferFrom(sfc, dfc.position(), sfc.size());
        // }
        // }
        // } finally {
        // FileUtil.delete(fimg.getFile());
        // }
        // }
        // }
        // if (raw != null) {
        // long length = raw.getFile().length() - FileRawImage.HEADER_LENGTH;
        // data.setBulkData(Tag.PixelData, VR.OB,
        // raw.getFile().toURI().toString() + "#offset=" + FileRawImage.HEADER_LENGTH + "&length=" + length,
        // null);
        // }
        // } catch (Exception e) {
        // if (raw != null) {
        // raw = null;
        // }
        // LOGGER.error("Writing raw pixel data", e);
        // } finally {
        // if (raw != null) {
        // try {
        // adaptTagsToImage(data, images.get(0), desc);
        // dos.writeDataSet(data);
        // } catch (Exception e) {
        // LOGGER.error("Writing dicom dataSet", e);
        // }
        // FileUtil.delete(raw.getFile());
        // }
        // }
        //

        try {
            // TODO try to not handle multiframe in memory
            PlanarImage img = images.get(0);
            int imgSize = img.width() * img.height() * (int) img.elemSize();
            ByteBuffer buf = ByteBuffer.allocate(images.size() * imgSize);
            for (PlanarImage image : images) {
                byte[] bSrcData = new byte[imgSize];
                img = DicomImageUtils.bgr2rgb(image);
                img.get(0, 0, bSrcData);
                buf.put(bSrcData);
            }
            data.setBytes(Tag.PixelData, VR.OB, buf.array());
            adaptTagsToImage(data, images.get(0), desc);
            dos.writeDataSet(data);
        } catch (Exception e) {
            LOGGER.error("Writing raw pixel data", e);
        }
    }

    public static void adaptTagsToImage(DicomObject data, PlanarImage img, ImageDescriptor desc) {
        data.setInt(Tag.Columns, VR.US, img.width());
        data.setInt(Tag.Rows, VR.US, img.height());
        data.setInt(Tag.SamplesPerPixel, VR.US, img.channels());
        data.setInt(Tag.BitsAllocated, VR.US, desc.getBitsAllocated());
        data.setInt(Tag.BitsStored, VR.US, desc.getBitsStored());
        data.setInt(Tag.HighBit, VR.US, desc.getBitsStored() - 1);
        data.setInt(Tag.PixelRepresentation, VR.US, desc.isSigned() ? 1 : 0);
        String pmi = desc.getPhotometricInterpretation().toString();
        if (img.channels() > 1) {
            pmi = PhotometricInterpretation.RGB.toString();
            data.setInt(Tag.PlanarConfiguration, VR.US, 0);
        }
        data.setString(Tag.PhotometricInterpretation, VR.CS, pmi);
    }

    public static boolean isNativeSyntax(String uid) {
        switch (uid) {
            case UID.ImplicitVRLittleEndian:
            case UID.ExplicitVRLittleEndian:
                return true;
            default:
                return false;
        }
    }

    public static boolean isSupportedSyntax(String uid) {
        switch (uid) {
            case UID.ImplicitVRLittleEndian:
            case UID.ExplicitVRLittleEndian:
            case UID.JPEGBaseline1:
            case UID.JPEGExtended24:
            case UID.JPEGSpectralSelectionNonHierarchical68Retired:
            case UID.JPEGFullProgressionNonHierarchical1012Retired:
            case UID.JPEGLosslessNonHierarchical14:
            case UID.JPEGLossless:
            case UID.JPEGLSLossless:
            case UID.JPEGLSLossyNearLossless:
            case UID.JPEG2000LosslessOnly:
            case UID.JPEG2000:
                // case UID.JPEG2000Part2MultiComponentLosslessOnly:
                // case UID.JPEG2000Part2MultiComponent:
                return true;
            default:
                return false;
        }
    }
}
