package org.dcm4che6.img;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;

import org.dcm4che6.data.DataFragment;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.img.data.PhotometricInterpretation;
import org.dcm4che6.img.data.TransferSyntaxType;
import org.dcm4che6.img.stream.BytesWithImageDescriptor;
import org.dcm4che6.img.stream.DicomFileInputStream;
import org.dcm4che6.img.stream.ExtendSegmentedInputImageStream;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.img.stream.SOFSegment;
import org.dcm4che6.img.stream.SegmentInputStream;
import org.dcm4che6.img.util.FileUtil;
import org.dcm4che6.io.ByteOrder;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.osgi.OpenCVNativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

/**
 * Reads image data from a DICOM object.
 * 
 * Supports all the DICOM objects containing pixel data. Use the OpenCV native library to read compressed and
 * uncompressed pixel data.
 * 
 * 
 * @author Nicolas Roduit
 * @since Jan 2020
 *
 */
public class DicomImageReader extends ImageReader implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(DicomImageReader.class);

    static {
        // Load the native OpenCV library
        OpenCVNativeLoader loader = new OpenCVNativeLoader();
        loader.init();
    }

    private final ArrayList<Integer> fragmentsPositions = new ArrayList<>();

    private BytesWithImageDescriptor bdis;
    private DicomFileInputStream dis;

    public DicomImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        resetInternalState();
        if (input instanceof DicomFileInputStream) {
            this.dis = (DicomFileInputStream) input;
        } else if (input instanceof BytesWithImageDescriptor) {
            this.bdis = (BytesWithImageDescriptor) input;
        } else {
            throw new IllegalArgumentException("Usupported inputStream: " + input.getClass().getName());
        }
    }

    public ImageDescriptor getImageDescriptor() {
        if (bdis != null)
            return bdis.getImageDescriptor();
        return dis.getImageDescriptor();
    }

    /**
     * Returns the number of regular images in the study. This excludes overlays.
     */
    @Override
    public int getNumImages(boolean allowSearch) {
        return getImageDescriptor().getFrames();
    }

    @Override
    public int getWidth(int frameIndex) {
        checkIndex(frameIndex);
        return getImageDescriptor().getColumns();
    }

    @Override
    public int getHeight(int frameIndex) {
        checkIndex(frameIndex);
        return getImageDescriptor().getRows();
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int frameIndex) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ImageReadParam getDefaultReadParam() {
        return new DicomImageReadParam();
    }

    /**
     * Gets the stream metadata. May not contain post pixel data unless there are no images or the getStreamMetadata has
     * been called with the post pixel data node being specified.
     */
    @Override
    public DicomMetaData getStreamMetadata() throws IOException {
        return dis == null ? null : dis.getMetadata();
    }

    @Override
    public IIOMetadata getImageMetadata(int frameIndex) {
        return null;
    }

    @Override
    public boolean canReadRaster() {
        return true;
    }

    @Override
    public Raster readRaster(int frameIndex, ImageReadParam param) {
        try {
            PlanarImage img = getPlanarImage(frameIndex, getDefaultReadParam(param));
            return ImageConversion.toBufferedImage(img).getRaster();
        } catch (Exception e) {
            LOG.error("Reading image", e);
            return null;
        }
    }

    @Override
    public BufferedImage read(int frameIndex, ImageReadParam param) {
        try {
            PlanarImage img = getPlanarImage(frameIndex, getDefaultReadParam(param));
            return ImageConversion.toBufferedImage(img);
        } catch (Exception e) {
            LOG.error("Reading image", e);
            return null;
        }
    }

    protected DicomImageReadParam getDefaultReadParam(ImageReadParam param) {
        DicomImageReadParam dcmParam;
        if (param instanceof DicomImageReadParam) {
            dcmParam = (DicomImageReadParam) param;
        } else {
            dcmParam = new DicomImageReadParam(param);
        }
        return dcmParam;
    }

    private void resetInternalState() {
        FileUtil.safeClose(dis);
        dis = null;
        bdis = null;
        fragmentsPositions.clear();
    }

    private void checkIndex(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= getImageDescriptor().getFrames())
            throw new IndexOutOfBoundsException("imageIndex: " + frameIndex);
    }

    @Override
    public void dispose() {
        resetInternalState();
    }

    @Override
    public void close() {
        dispose();
    }

    private boolean ybr2rgb(PhotometricInterpretation pmi, ExtendSegmentedInputImageStream seg) {
        switch (pmi) {
            case MONOCHROME1:
            case MONOCHROME2:
            case PALETTE_COLOR:
                return false;
            default:
                break;
        }
        if (dis != null) {
            try (SegmentInputStream stream = new SegmentInputStream(new RandomAccessFile(dis.getFile(), "r"),
                seg.getSegmentPositions()[0], seg.getSegmentLengths()[0])) {
                SOFSegment sof = SOFSegment.getSOFSegment(stream);
                // Preserve YBR for JPEG Lossless (1.2.840.10008.1.2.4.57, 1.2.840.10008.1.2.4.70)
                if (sof.getMarker() == 0xffc3) {
                    return false;
                }
                if (pmi == PhotometricInterpretation.RGB) {
                    // Force JPEG Baseline (1.2.840.10008.1.2.4.50) to YBR_FULL_422 color model when RGB with JFIF
                    // header (error made by some constructors). RGB color model doesn't make sense for lossy jpeg with
                    // JFIF header.
                    return sof.isJFIF() && sof.getMarker() == 0xffc0;
                }
            } catch (IOException e) {
                LOG.error("Cannort read jpeg header", e);
            }
        }
        return true;
    }

    public List<PlanarImage> getPlanarImages() throws IOException {
        return getPlanarImages(new DicomImageReadParam());
    }

    public List<PlanarImage> getPlanarImages(DicomImageReadParam param) throws IOException {
        List<PlanarImage> list = new ArrayList<>();
        for (int i = 0; i < dis.getMetadata().getImageDescriptor().getFrames(); i++) {
            list.add(getPlanarImage(i, param));
        }
        return list;
    }

    public PlanarImage getPlanarImage() throws IOException {
        return getPlanarImage(0, new DicomImageReadParam());
    }

    public PlanarImage getPlanarImage(int frame, DicomImageReadParam param) throws IOException {
        PlanarImage img = getRawImage(frame, param);
        if (getImageDescriptor().getPhotometricInterpretation() == PhotometricInterpretation.PALETTE_COLOR) {
            img = DicomImageUtils.getRGBImageFromPaletteColorModel(img, dis.getMetadata().getDicomObject());
        }

        /*
         * Handle overlay in pixel data: extract the overlay, serialize it in a file and set all values to O in the
         * pixel data.
         */
        return dis.getImageWithoutEmbeddedOverlay(img);
    }

    public PlanarImage getRawImage(int frame, DicomImageReadParam param) throws IOException {
        if (dis == null) {
            return getRawImageFromBytes(param);
        } else {
            return getRawImageFromFile(frame, param);
        }
    }

    protected PlanarImage getRawImageFromFile(int frame, DicomImageReadParam param) throws IOException {
        if (dis == null) {
            throw new IOException("No DicomInputStream found");
        }
        DicomObject dcm = dis.getMetadata().getDicomObject();
        Optional<DicomElement> pixdata = dcm.get(Tag.PixelData);
        boolean floatPixData = false;
        if (pixdata.isEmpty()) {
            Optional<DicomElement> fdata = dcm.get(Tag.FloatPixelData);
            if (fdata.isEmpty()) {
                fdata = dcm.get(Tag.DoubleFloatPixelData);
            }
            if (fdata.isPresent()) {
                floatPixData = true;
                pixdata = fdata;
            }
        }

        ImageDescriptor desc = getImageDescriptor();
        int bitsStored = desc.getBitsStored();
        if (pixdata.isEmpty() || bitsStored < 1) {
            throw new IllegalStateException("No pixel data in this DICOM object");
        }

        List<DataFragment> fragments = pixdata.get().fragmentStream().collect(Collectors.toList());

        ExtendSegmentedInputImageStream seg =
            buildSegmentedImageInputStream(frame, fragments, pixdata.get(), dis.getEncoding().explicitVR);
        if (seg.getSegmentPositions() == null) {
            return null;
        }

        String tsuid = dis.getMetadata().getTransferSyntaxUID().orElse(dis.getEncoding().transferSyntaxUID);
        TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
        boolean rawData = fragments.isEmpty() || type == TransferSyntaxType.NATIVE || type == TransferSyntaxType.RLE;
        int dcmFlags = (type.canEncodeSigned() && desc.isSigned()) ? Imgcodecs.DICOM_IMREAD_SIGNED
            : Imgcodecs.DICOM_IMREAD_UNSIGNED;
        if (!rawData && !TransferSyntaxType.isJpeg2000(tsuid) && ybr2rgb(desc.getPhotometricInterpretation(), seg)) {
            dcmFlags |= Imgcodecs.DICOM_IMREAD_YBR;
        }
        boolean bigendian = dis.getEncoding().byteOrder == ByteOrder.BIG_ENDIAN;
        if (bigendian) {
            dcmFlags |= Imgcodecs.DICOM_IMREAD_BIGENDIAN;
        }
        if (floatPixData) {
            dcmFlags |= Imgcodecs.DICOM_IMREAD_FLOAT;
        }
        if (UID.RLELossless.equals(tsuid)) {
            dcmFlags |= Imgcodecs.DICOM_IMREAD_RLE;
        }
        // TODO implement  param.getSourceRegion() & param.getSourceRenderSize()

        MatOfDouble positions = null;
        MatOfDouble lengths = null;
        try {
            positions = new MatOfDouble(Arrays.stream(seg.getSegmentPositions()).asDoubleStream().toArray());
            lengths = new MatOfDouble(Arrays.stream(seg.getSegmentLengths()).asDoubleStream().toArray());
            if (rawData) {
                int bits = bitsStored <= 8 && desc.getBitsAllocated() > 8 ? 9 : bitsStored; // Fix #94
                MatOfInt dicomparams = new MatOfInt(Imgcodecs.IMREAD_UNCHANGED, dcmFlags, desc.getColumns(),
                    desc.getRows(), Imgcodecs.DICOM_CP_UNKNOWN, desc.getSamples(), bits,
                    desc.isBanded() ? Imgcodecs.ILV_NONE : Imgcodecs.ILV_SAMPLE);
                return ImageCV.toImageCV(Imgcodecs.dicomRawFileRead(seg.getFile().getAbsolutePath(), positions, lengths,
                    dicomparams, desc.getPhotometricInterpretation().name()));
            }
            return ImageCV.toImageCV(Imgcodecs.dicomJpgFileRead(seg.getFile().getAbsolutePath(), positions, lengths,
                dcmFlags, Imgcodecs.IMREAD_UNCHANGED));
        } finally {
            closeMat(positions);
            closeMat(lengths);
        }
    }

    protected PlanarImage getRawImageFromBytes(DicomImageReadParam param) throws IOException {
        if (bdis == null) {
            throw new IOException("No BytesWithImageDescriptor found");
        }

        ImageDescriptor desc = getImageDescriptor();
        int bitsStored = desc.getBitsStored();
        boolean floatPixData = false; // TODO getFloatPixel

        String tsuid = bdis.getTransferSyntax();
        TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
        boolean rawData = type == TransferSyntaxType.NATIVE || type == TransferSyntaxType.RLE;
        int dcmFlags = (type.canEncodeSigned() && desc.isSigned()) ? Imgcodecs.DICOM_IMREAD_SIGNED
            : Imgcodecs.DICOM_IMREAD_UNSIGNED;
        if (!rawData && !TransferSyntaxType.isJpeg2000(tsuid) && bdis.forceYbrToRgbConversion()) {
            dcmFlags |= Imgcodecs.DICOM_IMREAD_YBR;
        }
        boolean bigendian = dis.getEncoding().byteOrder == ByteOrder.BIG_ENDIAN;
        if (bigendian) {
            dcmFlags |= Imgcodecs.DICOM_IMREAD_BIGENDIAN;
        }
        if (floatPixData) {
            dcmFlags |= Imgcodecs.DICOM_IMREAD_FLOAT;
        }
        if (UID.RLELossless.equals(tsuid)) {
            dcmFlags |= Imgcodecs.DICOM_IMREAD_RLE;
        }
        // TODO implement  param.getSourceRegion() & param.getSourceRenderSize()

        Mat buf = null;
        try {
            ByteBuffer b = bdis.getBytes();
            buf = new Mat(1, b.limit(), CvType.CV_8UC1);
            buf.put(0, 0, b.array());
            if (rawData) {
                int bits = bitsStored <= 8 && desc.getBitsAllocated() > 8 ? 9 : bitsStored; // Fix #94
                MatOfInt dicomparams = new MatOfInt(Imgcodecs.IMREAD_UNCHANGED, dcmFlags, desc.getColumns(),
                    desc.getRows(), Imgcodecs.DICOM_CP_UNKNOWN, desc.getSamples(), bits,
                    desc.isBanded() ? Imgcodecs.ILV_NONE : Imgcodecs.ILV_SAMPLE);
                return ImageCV
                    .toImageCV(Imgcodecs.dicomRawMatRead(buf, dicomparams, desc.getPhotometricInterpretation().name()));
            }
            return ImageCV.toImageCV(Imgcodecs.dicomJpgMatRead(buf, dcmFlags, Imgcodecs.IMREAD_UNCHANGED));
        } finally {
            closeMat(buf);
        }
    }

    public static void closeMat(Mat mat) {
        if (mat != null) {
            mat.release();
        }
    }

    private ExtendSegmentedInputImageStream buildSegmentedImageInputStream(int frameIndex, List<DataFragment> fragments,
        DicomElement pixelData, boolean explicitVR) throws IOException {
        long[] offsets;
        int[] length;
        ImageDescriptor desc = getImageDescriptor();
        if (fragments.isEmpty()) {
            // Do not use pixelData.valueLength() because of multiframe in float pixel data
            int frameLength = desc.getPhotometricInterpretation().frameLength(desc.getColumns(), desc.getRows(),
                desc.getSamples(), desc.getBitsAllocated());
            offsets = new long[1];
            length = new int[offsets.length];
            long offset = pixelData.getStreamPosition() + (!explicitVR || pixelData.vr().shortValueLength ? 8 : 12);
            length[0] = frameLength;
            offsets[0] = offset + frameIndex * length[0];
        } else {
            int nbFragments = fragments.size();
            int numberOfFrame = desc.getFrames();

            if (numberOfFrame >= nbFragments - 1) {
                // nbFrames > nbFragments should never happen
                offsets = new long[1];
                length = new int[offsets.length];
                int index = frameIndex < nbFragments - 1 ? frameIndex + 1 : nbFragments - 1;
                DataFragment bulkData = fragments.get(index);
                offsets[0] = bulkData.valuePosition();
                length[0] = bulkData.valueLength();
            } else {
                if (numberOfFrame == 1) {
                    offsets = new long[nbFragments - 1];
                    length = new int[offsets.length];
                    for (int i = 0; i < length.length; i++) {
                        DataFragment bulkData = fragments.get(i + frameIndex + 1);
                        offsets[i] = bulkData.valuePosition();
                        length[i] = bulkData.valueLength();
                    }
                } else {
                    // Multi-frames where each frames can have multiple fragments.
                    if (fragmentsPositions.isEmpty()) {
                        String tsuid =
                            dis.getMetadata().getTransferSyntaxUID().orElse(dis.getEncoding().transferSyntaxUID);
                        boolean jpeg2000 = tsuid.startsWith("1.2.840.10008.1.2.4.9");
                        try (RandomAccessFile srcStream = new RandomAccessFile(dis.getFile(), "r")) {
                            for (int i = 1; i < nbFragments; i++) {
                                DataFragment bulkData = fragments.get(i);
                                SegmentInputStream stream =
                                    new SegmentInputStream(srcStream, bulkData.valuePosition(), bulkData.valueLength());
                                if (jpeg2000 ? decodeJpeg2000(stream) : decodeJpeg(stream)) {
                                    fragmentsPositions.add(i);
                                }
                            }
                        }
                    }

                    if (fragmentsPositions.size() == numberOfFrame) {
                        int start = fragmentsPositions.get(frameIndex);
                        int end = (frameIndex + 1) >= fragmentsPositions.size() ? nbFragments
                            : fragmentsPositions.get(frameIndex + 1);

                        offsets = new long[end - start];
                        length = new int[offsets.length];
                        for (int i = 0; i < offsets.length; i++) {
                            DataFragment bulkData = fragments.get(start + i);
                            offsets[i] = bulkData.valuePosition();
                            length[i] = bulkData.valueLength();
                        }
                    } else {
                        throw new IOException("Cannot match all the fragments to all the frames!");
                    }
                }
            }
        }
        return new ExtendSegmentedInputImageStream(dis.getFile(), offsets, length, desc);
    }

    private static boolean decodeJpeg2000(SegmentInputStream iis) throws IOException {
        iis.mark();
        try {
            int marker = (iis.read() << 8) | iis.read();

            if (marker == 0xFF4F) {
                return true;
            }

            iis.reset();
            iis.mark();
            byte[] b = new byte[12];
            iis.read(b, 0, b.length);

            // Verify the signature box
            // The length of the signature box is 12
            if (b[0] != 0 || b[1] != 0 || b[2] != 0 || b[3] != 12) {
                return false;
            }

            // The signature box type is "jP "
            if ((b[4] & 0xff) != 0x6A || (b[5] & 0xFF) != 0x50 || (b[6] & 0xFF) != 0x20 || (b[7] & 0xFF) != 0x20) {
                return false;
            }

            // The signature content is 0x0D0A870A
            return (b[8] & 0xFF) == 0x0D && (b[9] & 0xFF) == 0x0A && (b[10] & 0xFF) == 0x87 && (b[11] & 0xFF) == 0x0A;
        } finally {
            iis.reset();
        }
    }

    private static boolean decodeJpeg(SegmentInputStream iis) throws IOException {
        // jpeg and jpeg-ls
        iis.mark();
        try {
            int byte1 = iis.read();
            int byte2 = iis.read();
            // Magic numbers for JPEG (general jpeg marker)
            if ((byte1 != 0xFF) || (byte2 != 0xD8)) {
                return false;
            }
            do {
                byte1 = iis.read();
                byte2 = iis.read();
                // Something wrong, but try to read it anyway
                if (byte1 != 0xFF) {
                    break;
                }
                // Start of scan
                if (byte2 == 0xDA) {
                    break;
                }
                // Start of Frame, also known as SOF55, indicates a JPEG-LS file.
                if (byte2 == 0xF7) {
                    return true;
                }
                // 0xffc0: // SOF_0: JPEG baseline
                // 0xffc1: // SOF_1: JPEG extended sequential DCT
                // 0xffc2: // SOF_2: JPEG progressive DCT
                // 0xffc3: // SOF_3: JPEG lossless sequential
                if ((byte2 >= 0xC0) && (byte2 <= 0xC3)) {
                    return true;
                }
                // 0xffc5: // SOF_5: differential (hierarchical) extended sequential, Huffman
                // 0xffc6: // SOF_6: differential (hierarchical) progressive, Huffman
                // 0xffc7: // SOF_7: differential (hierarchical) lossless, Huffman
                if ((byte2 >= 0xC5) && (byte2 <= 0xC7)) {
                    return true;
                }
                // 0xffc9: // SOF_9: extended sequential, arithmetic
                // 0xffca: // SOF_10: progressive, arithmetic
                // 0xffcb: // SOF_11: lossless, arithmetic
                if ((byte2 >= 0xC9) && (byte2 <= 0xCB)) {
                    return true;
                }
                // 0xffcd: // SOF_13: differential (hierarchical) extended sequential, arithmetic
                // 0xffce: // SOF_14: differential (hierarchical) progressive, arithmetic
                // 0xffcf: // SOF_15: differential (hierarchical) lossless, arithmetic
                if ((byte2 >= 0xCD) && (byte2 <= 0xCF)) {
                    return true;
                }
                int length = iis.read() << 8;
                length += iis.read();
                length -= 2;
                while (length > 0) {
                    length -= iis.skip(length);
                }
            } while (true);
            return true;
        } finally {
            iis.reset();
        }
    }

}
