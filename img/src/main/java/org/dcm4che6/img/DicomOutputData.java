package org.dcm4che6.img;

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
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.PlanarImage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Nicolas Roduit
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

    public void writCompressedImageData(DicomOutputStream dos, DicomObject dataSet, int[] params) throws IOException {
        Mat buf = null;
        MatOfInt dicomParams = null;
        boolean first = true;
        try {
            dicomParams = new MatOfInt(params);
            for (PlanarImage image : images) {
                buf = Imgcodecs.dicomJpgWrite(DicomImageUtils.bgr2rgb(image).toMat(), dicomParams, "");
                if (buf.empty()) {
                    throw new IOException("Native encoding error: null image");
                }
                int compressedLength = buf.width() * buf.height() * (int) buf.elemSize();
                if (first) {
                    first = false;
                    double uncompressed = image.width() * image.height() * (double) image.elemSize();
                    adaptCompressionRatio(dataSet, params, uncompressed / compressedLength);
                    dos.writeDataSet(dataSet);
                    dos.writeHeader(Tag.PixelData, VR.OB, -1);
                    dos.writeHeader(Tag.Item, VR.NONE, 0);
                }

                byte[] bSrcData = new byte[compressedLength];
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

    private void adaptCompressionRatio(DicomObject dataSet, int[] params, double ratio) {
        int compressType = params[Imgcodecs.DICOM_PARAM_COMPRESSION];
        int jpeglsNLE = params[Imgcodecs.DICOM_PARAM_JPEGLS_LOSSY_ERROR];
        int jpeg2000CompRatio = params[Imgcodecs.DICOM_PARAM_J2K_COMPRESSION_FACTOR];
        int jpegQuality = params[Imgcodecs.DICOM_PARAM_JPEG_QUALITY];
        if ((compressType == Imgcodecs.DICOM_CP_JPG && jpegQuality > 0) || (compressType == Imgcodecs.DICOM_CP_J2K
                && jpeg2000CompRatio > 0) || (compressType == Imgcodecs.DICOM_CP_JPLS && jpeglsNLE > 0)) {
            dataSet.setString(Tag.LossyImageCompression, VR.CS, "01");
            String method = compressType == Imgcodecs.DICOM_CP_J2K ? "ISO_15444_1"
                    : compressType == Imgcodecs.DICOM_CP_JPLS ? "ISO_14495_1" : "ISO_10918_1";
            double[] old = dataSet.getDoubles(Tag.LossyImageCompressionRatio).orElse(null);
            double[] destArray;
            String[] methods;
            if (old == null) {
                destArray = new double[]{ratio};
                methods = new String[]{method};
            } else {
                destArray = Arrays.copyOf(old, old.length + 1);
                destArray[destArray.length - 1] = ratio;
                String[] oldM = dataSet.getStrings(Tag.LossyImageCompressionMethod).orElseGet(() -> new String[0]);
                methods = Arrays.copyOf(oldM, old.length + 1);
                methods[methods.length - 1] = method;
                for (int i = 0; i < methods.length; i++) {
                    if (!StringUtil.hasText(methods[i])) {
                        methods[i] = "unknown";
                    }
                }
            }
            dataSet.setDouble(Tag.LossyImageCompressionRatio, VR.DS, destArray);
            dataSet.setString(Tag.LossyImageCompressionMethod, VR.CS, methods);
        }
    }

    public void writRawImageData(DicomOutputStream dos, DicomObject data) {
        try {
            PlanarImage img = images.get(0);
            adaptTagsToRawImage(data, img, desc);
            dos.writeDataSet(data);

            int type = CvType.depth(img.type());
            int imgSize = img.width() * img.height();
            int channels = CvType.channels(img.type());
            int length = images.size() * imgSize * (int) img.elemSize();
            dos.writeHeader(Tag.PixelData, VR.OB, length);

            if (type <= CvType.CV_8S) {
                byte[] srcData = new byte[imgSize * channels];
                for (PlanarImage image : images) {
                    img = DicomImageUtils.bgr2rgb(image);
                    img.get(0, 0, srcData);
                    dos.write(srcData);
                }
            } else if (type <= CvType.CV_16S) {
                short[] srcData = new short[imgSize * channels];
                ByteBuffer bb = ByteBuffer.allocate(srcData.length * 2);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                for (PlanarImage image : images) {
                    img = DicomImageUtils.bgr2rgb(image);
                    img.get(0, 0, srcData);
                    bb.asShortBuffer().put(srcData);
                    dos.write(bb.array());
                }
            } else if (type == CvType.CV_32S) {
                int[] srcData = new int[imgSize * channels];
                ByteBuffer bb = ByteBuffer.allocate(srcData.length * 4);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                for (PlanarImage image : images) {
                    img = DicomImageUtils.bgr2rgb(image);
                    img.get(0, 0, srcData);
                    bb.asIntBuffer().put(srcData);
                    dos.write(bb.array());
                }
            } else if (type == CvType.CV_32F) {
                float[] srcData = new float[imgSize * channels];
                ByteBuffer bb = ByteBuffer.allocate(srcData.length * 4);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                for (PlanarImage image : images) {
                    img = DicomImageUtils.bgr2rgb(image);
                    img.get(0, 0, srcData);
                    bb.asFloatBuffer().put(srcData);
                    dos.write(bb.array());
                }
            } else if (type == CvType.CV_64F) {
                double[] srcData = new double[imgSize * channels];
                ByteBuffer bb = ByteBuffer.allocate(srcData.length * 8);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                for (PlanarImage image : images) {
                    img = DicomImageUtils.bgr2rgb(image);
                    img.get(0, 0, srcData);
                    bb.asDoubleBuffer().put(srcData);
                    dos.write(bb.array());
                }
            } else {
                throw new IllegalStateException("Cannot write this unknown image type");
            }
        } catch (Exception e) {
            LOGGER.error("Writing raw pixel data", e);
        }
    }

    public static void adaptTagsToRawImage(DicomObject data, PlanarImage img, ImageDescriptor desc) {
        int cvType = img.type();
        int channels = CvType.channels(cvType);
        int signed = CvType.depth(cvType) == CvType.CV_16S || desc.isSigned() ? 1 : 0;
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

    public int[] adaptTagsToCompressedImage(DicomObject data, PlanarImage img, ImageDescriptor desc,
                                            DicomJpegWriteParam param) {
        int cvType = img.type();
        int elemSize = (int) img.elemSize1();
        int channels = CvType.channels(cvType);
        int depth = CvType.depth(cvType);
        boolean signed = depth != CvType.CV_8U && (depth != CvType.CV_16U || desc.isSigned());
        int dcmFlags = signed ? Imgcodecs.DICOM_FLAG_SIGNED : Imgcodecs.DICOM_FLAG_UNSIGNED;
        PhotometricInterpretation pmi = desc.getPhotometricInterpretation();
        int epi = channels == 1 ? Imgcodecs.EPI_Monochrome2 : Imgcodecs.EPI_RGB;
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
                // Extend to bit allocated to avoid exception as negative values are treated as large positive values
                bitCompressedForEncoder = 16;
            }
        } else {
            // JPEG encoder
            if (bitCompressed <= 8) {
                bitCompressedForEncoder = bitCompressed = 8;
            } else if (bitCompressed <= 12) {
                if (signed && param.getPrediction() > 1) {
                    LOGGER.warn("Force JPEGLosslessNonHierarchical14 compression to 16-bit with signed data.");
                    bitCompressed = 12;
                    bitCompressedForEncoder = 16;
                } else {
                    bitCompressedForEncoder = bitCompressed = 12;
                }
            } else {
                bitCompressedForEncoder = bitCompressed = 16;
            }
        }

        // Specific case not well supported by jpeg and jpeg-ls encoder that reduce the stream to 8-bit
        if (ts != TransferSyntaxType.JPEG_2000 && bitCompressed == 8 && bitAllocated == 16) {
            bitCompressedForEncoder = 12;
        }

        int[] params = new int[16];
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
        params[Imgcodecs.DICOM_PARAM_J2K_COMPRESSION_FACTOR] = param.getCompressionRatiofactor(); // JPEG2000 factor of compression ratio
        params[Imgcodecs.DICOM_PARAM_JPEG_QUALITY] = param.getCompressionQuality(); // JPEG lossy quality
        params[Imgcodecs.DICOM_PARAM_JPEG_PREDICTION] = param.getPrediction(); // JPEG lossless prediction
        params[Imgcodecs.DICOM_PARAM_JPEG_PT_TRANSFORM] = param.getPointTransform(); // JPEG lossless transformation point

        data.setInt(Tag.Columns, VR.US, img.width());
        data.setInt(Tag.Rows, VR.US, img.height());
        data.setInt(Tag.SamplesPerPixel, VR.US, channels);
        data.setInt(Tag.BitsAllocated, VR.US, bitAllocated);
        data.setInt(Tag.BitsStored, VR.US, bitCompressed);
        data.setInt(Tag.HighBit, VR.US, bitCompressed - 1);
        data.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        if (img.channels() > 1) {
            data.setInt(Tag.PlanarConfiguration, VR.US, 0);
            pmi = PhotometricInterpretation.RGB.compress(tsuid);
        }
        data.setString(Tag.PhotometricInterpretation, VR.CS, pmi.toString());
        return params;
    }


    public static String adaptSuitableSyntax(int bitStored, int type, String dstTsuid) {
        switch (dstTsuid) {
            case UID.ImplicitVRLittleEndian:
            case UID.ExplicitVRLittleEndian:
                return UID.ExplicitVRLittleEndian;
            case UID.JPEGBaseline1:
                return type <= CvType.CV_8S ? dstTsuid : type <= CvType.CV_16S ? UID.JPEGLossless : UID.ExplicitVRLittleEndian;
            case UID.JPEGExtended24:
            case UID.JPEGSpectralSelectionNonHierarchical68Retired:
            case UID.JPEGFullProgressionNonHierarchical1012Retired:
                return type <= CvType.CV_16U && bitStored <= 12 ? dstTsuid :
                        type <= CvType.CV_16S ? UID.JPEGLossless : UID.ExplicitVRLittleEndian;
            case UID.JPEGLosslessNonHierarchical14:
            case UID.JPEGLossless:
            case UID.JPEGLSLossless:
            case UID.JPEGLSLossyNearLossless:
            case UID.JPEG2000LosslessOnly:
            case UID.JPEG2000:
                return type <= CvType.CV_16S ? dstTsuid : UID.ExplicitVRLittleEndian;
            default:
                return dstTsuid;
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
