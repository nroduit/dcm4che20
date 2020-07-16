package org.dcm4che6.img;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    public List<PlanarImage> getImages() {
        return images;
    }

    public String getTsuid() {
        return tsuid;
    }

    public void writCompressedImageData(DicomOutputStream dos, int[] params) throws IOException {
        dos.writeHeader(Tag.PixelData, VR.OB, -1);
        dos.writeHeader(Tag.Item, VR.NONE, 0);

        Mat buf = null;
        MatOfInt dicomParams = null;
        try {
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

    public void writRawImageData(DicomOutputStream dos, DicomObject data) {
        try {
            PlanarImage img = images.get(0);
            adaptTagsToImage(data, img, desc);
            dos.writeDataSet(data);

            int type = CvType.depth(img.type());
            int imgSize = img.width() * img.height();
            int length = images.size() * imgSize * (int) img.elemSize();
            dos.writeHeader(Tag.PixelData, VR.OB, length);

            if (type <= CvType.CV_8S) {
                byte[] bSrcData = new byte[imgSize];
                for (PlanarImage image : images) {
                    img = DicomImageUtils.bgr2rgb(image);
                    img.get(0, 0, bSrcData);
                    dos.write(bSrcData);
                }
            } else if (type <= CvType.CV_16S) {
                short[] bSrcData = new short[imgSize];
                ByteBuffer bb = ByteBuffer.allocate(bSrcData.length * 2);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                for (PlanarImage image : images) {
                    img = DicomImageUtils.bgr2rgb(image);
                    img.get(0, 0, bSrcData);
                    bb.asShortBuffer().put(bSrcData);
                    dos.write(bb.array());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Writing raw pixel data", e);
        }
    }

    public static void adaptTagsToImage(DicomObject data, PlanarImage img, ImageDescriptor desc) {
        int cvType = img.type();
        int channels = CvType.channels(cvType);
        int signed = CvType.depth(cvType) == CvType.CV_16S ? 1 : 0;
        data.setInt(Tag.Columns, VR.US, img.width());
        data.setInt(Tag.Rows, VR.US, img.height());
        data.setInt(Tag.SamplesPerPixel, VR.US, channels);
        data.setInt(Tag.BitsAllocated, VR.US, desc.getBitsAllocated());
        data.setInt(Tag.BitsStored, VR.US, desc.getBitsStored());
        data.setInt(Tag.HighBit, VR.US, desc.getBitsStored() - 1);
        data.setInt(Tag.PixelRepresentation, VR.US, signed);
        String pmi = desc.getPhotometricInterpretation().toString();
        if (img.channels() > 1) {
            pmi = PhotometricInterpretation.RGB.toString();
            data.setInt(Tag.PlanarConfiguration, VR.US, 0);
        }
        data.setString(Tag.PhotometricInterpretation, VR.CS, pmi);
    }

    public int[] adaptTagsToImage(DicomObject data, PlanarImage img, ImageDescriptor desc,
        DicomJpegWriteParam param) {

        int cvType = img.type();
        int elemSize = (int) img.elemSize1();
        int channels = CvType.channels(cvType);
        int depth = CvType.depth(cvType);
        boolean signed = depth != CvType.CV_8U && depth != CvType.CV_16U;
        int dcmFlags = signed ? Imgcodecs.DICOM_FLAG_SIGNED : Imgcodecs.DICOM_FLAG_UNSIGNED;
        PhotometricInterpretation pmi = desc.getPhotometricInterpretation();
        if (img.channels() > 1) {
            pmi = PhotometricInterpretation.RGB.compress(tsuid);
        }
        int epi = getCodecColorSpace(pmi);
        int bitAllocated = elemSize * 8;
        int bitCompressed = desc.getBitsCompressed();
        if (bitCompressed > bitAllocated) {
            bitCompressed = bitAllocated;
        }
        int bitCompressedForEncoder = bitCompressed;
        int jpeglsNLE = param.getNearLosslessError();
        TransferSyntaxType ts = param.getType();
        int compressType = Imgcodecs.DICOM_CP_JPG;
        if (ts == TransferSyntaxType.JPEG_2000) {
            compressType = Imgcodecs.DICOM_CP_J2K;
        } else if (ts == TransferSyntaxType.JPEG_LS) {
            compressType = Imgcodecs.DICOM_CP_JPLS;
            if (signed) {
                LOGGER.warn("Force compression to JPEG-LS lossless as lossy is not adapted to signed data.");
                jpeglsNLE = 0;
                bitCompressedForEncoder = 16; // Extend to bit allocated to avoid exception as negative values are
                                              // treated as
                // large positive values
            }
        } else {
            // JPEG encoder
            if (bitCompressed <= 8) {
                bitCompressed = 8;
            } else if (bitCompressed <= 12) {
                bitCompressed = 12;
            } else {
                bitCompressed = 16;
            }
            bitCompressedForEncoder = bitCompressed;
        }

        int[] params = new int[15];
        params[Imgcodecs.DICOM_PARAM_IMREAD] = Imgcodecs.IMREAD_UNCHANGED; // Image flags
        params[Imgcodecs.DICOM_PARAM_DCM_IMREAD] = dcmFlags; // DICOM flags
        params[Imgcodecs.DICOM_PARAM_WIDTH] = img.width(); // Image width
        params[Imgcodecs.DICOM_PARAM_HEIGHT] = img.height(); // Image height
        params[Imgcodecs.DICOM_PARAM_COMPRESSION] = compressType; // Type of compression
        params[Imgcodecs.DICOM_PARAM_COMPONENTS] = channels; // Number of components
        params[Imgcodecs.DICOM_PARAM_BITS_PER_SAMPLE] = bitCompressedForEncoder; // Bits per sample
        params[Imgcodecs.DICOM_PARAM_INTERLEAVE_MODE] = Imgcodecs.ILV_SAMPLE; // Interleave mode
        params[Imgcodecs.DICOM_PARAM_COLOR_MODEL] = epi; // Photometric interpretation
        params[Imgcodecs.DICOM_PARAM_JPEG_MODE] = param.getJpegMode(); // JPEG Codec mode
        params[Imgcodecs.DICOM_PARAM_JPEGLS_LOSSY_ERROR] = jpeglsNLE; // Lossy error for jpeg-ls
        params[Imgcodecs.DICOM_PARAM_J2K_COMPRESSION_FACTOR] = param.getCompressionRatiofactor(); // JPEG2000 factor of
                                                                                                  // compression ratio
        params[Imgcodecs.DICOM_PARAM_JPEG_QUALITY] = param.getCompressionQuality(); // JPEG lossy quality
        params[Imgcodecs.DICOM_PARAM_JPEG_PREDICTION] = param.getPrediction(); // JPEG lossless prediction
        params[Imgcodecs.DICOM_PARAM_JPEG_PT_TRANSFORM] = param.getPointTransform(); // JPEG lossless transformation
                                                                                     // point

        data.setInt(Tag.Columns, VR.US, img.width());
        data.setInt(Tag.Rows, VR.US, img.height());
        data.setInt(Tag.SamplesPerPixel, VR.US, channels);
        data.setInt(Tag.BitsAllocated, VR.US, bitAllocated);
        data.setInt(Tag.BitsStored, VR.US, bitCompressed);
        data.setInt(Tag.HighBit, VR.US, bitCompressed - 1);
        data.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        if (img.channels() > 1) {
            data.setInt(Tag.PlanarConfiguration, VR.US, 0);
        }
        data.setString(Tag.PhotometricInterpretation, VR.CS, pmi.toString());

        return params;
    }

    private static int getCodecColorSpace(PhotometricInterpretation pi) {
        if (PhotometricInterpretation.MONOCHROME1 == pi) {
            return Imgcodecs.EPI_Monochrome1;
        } else if (PhotometricInterpretation.MONOCHROME2 == pi) {
            return Imgcodecs.EPI_Monochrome2;
        } else if (PhotometricInterpretation.RGB == pi) {
            return Imgcodecs.EPI_RGB;
        } else if (PhotometricInterpretation.YBR_FULL == pi) {
            return Imgcodecs.EPI_YBR_Full;
        } else if (PhotometricInterpretation.YBR_FULL_422 == pi) {
            return Imgcodecs.EPI_YBR_Full_422;
        } else if (PhotometricInterpretation.YBR_PARTIAL_422 == pi) {
            return Imgcodecs.EPI_YBR_Partial_422;
        } else { // Palette, HSV, ARGB, CMYK
            return Imgcodecs.EPI_Unknown;
        }
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
