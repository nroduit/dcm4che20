package org.dcm4che6.img;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.dcm4che6.data.UID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author Nicolas Roduit
 *
 */
public class TranscodingTest {

    private static final File IN_DIR = new File(TranscodingTest.class.getResource("").getFile());
    private static final File OUT_DIR = new File("target/test-out/");
    static {
        OUT_DIR.mkdir();
        for (File f : OUT_DIR.listFiles())
            f.delete();
    }

    // @Test
    // public void testCopyIVR() throws Exception {
    // dcm2dcm("OT-PAL-8-face", "OT-PAL-8-face", UID.ImplicitVRLittleEndian, false);
    // }
    //
    // @Test
    // public void testCopyBigEndian() throws Exception {
    // dcm2dcm("US-RGB-8-epicard", "US-RGB-8-epicard", UID.ExplicitVRBigEndianRetired, true);
    // }
    //
    // @Test
    // public void testCopyDeflated() throws Exception {
    // dcm2dcm("report_dfl", "report_dfl", UID.DeflatedExplicitVRLittleEndian, true);
    // }
    //
    // @Test
    // public void testCopyJPEG12bit() throws Exception {
    // dcm2dcm("NM1_JPLY", "NM1_JPLY", UID.JPEGExtended24, true);
    // }
    //
    // @Test
    // public void testBigEndian2LittleEndian() throws Exception {
    // dcm2dcm("US-RGB-8-epicard", "US-RGB-8-epicard.littleEndian", UID.ImplicitVRLittleEndian, false);
    // }
    //
    // @Test
    // public void testDecompressJPEG12bit() throws Exception {
    // dcm2dcm("NM1_JPLY", "NM1_JPLY.unc", UID.ExplicitVRLittleEndian, true);
    // }
    //
    // @Test
    // public void testDecompressMF() throws Exception {
    // dcm2dcm("US-PAL-8-10x-echo", "US-PAL-8-10x-echo.unc", UID.ExplicitVRLittleEndian, true);
    // }
    //
    // @Test
    // public void testDecompressJpeglsPaletteMF() throws Exception {
    // dcm2dcm("jpeg-ls-Palette.dcm", "jpeg-ls-Palette-raw.dcm", UID.ExplicitVRLittleEndian, true);
    // }
    //
    // @Test
    // public void testCompressMF() throws Exception {
    // dcm2dcm("cplx_p02.dcm", "cplx_p02_jply.dcm", UID.JPEGBaseline1, true);
    // }
    //
    // @Test
    // public void testCompressEmbeddedOverlays() throws Exception {
    // dcm2dcm("ovly_p01.dcm", "ovly_p01_jply.dcm", UID.JPEGExtended24, true);
    // }
    //
    // @Test
    // public void testCompressPerPlaneRGB() throws Exception {
    // dcm2dcm("US-RGB-8-epicard", "US-RGB-8-epicard_jply", UID.JPEGBaseline1, true);
    // }
    //
    // @Test
    // public void testCompressPerPixelRGB() throws Exception {
    // dcm2dcm("US-RGB-8-esopecho", "US-RGB-8-esopecho_jply", UID.JPEGBaseline1, true);
    // }
    //
    // @Test
    // public void testCompressPerPixelRgb2JpegLossless() throws Exception {
    // dcm2dcm("US-RGB-8-esopecho", "US-RGB-8-esopecho-jpegLossless.dcm", UID.JPEGLossless, true);
    // }
    //
    // @Test
    // public void testTranscodePaletteRleMf2RgbJpegls() throws Exception {
    // dcm2dcm("US-PAL-8-10x-echo", "US-PAL-8-10x-echo-jpegls.dcm", UID.JPEGLSLossyNearLossless, true);
    // }
    //
    // @Test
    // public void testTranscodeJpeglsPaletteMf2RgbJ2k() throws Exception {
    // dcm2dcm("jpeg-ls-Palette.dcm", "jpeg-ls-Palette-j2k.dcm", UID.JPEG2000, true);
    // }
    //
    // @Test
    // public void testTranscodeYbrFullRle2RgbJ2k() throws Exception {
    // dcm2dcm("YBR_FULL-RLE.dcm", "YBR_FULL-RLE-j2k.dcm", UID.JPEG2000, true);
    // }
    //
    // @Test
    // public void testTranscodeYbr422Raw2RgbJpegLossless() throws Exception {
    // dcm2dcm("YYBR_422-Raw-Subsample.dcm", "YBR_422-jpegLossless.dcm", UID.JPEGLossless, true);
    // }

    @ParameterizedTest
    @ValueSource(strings = { UID.JPEG2000LosslessOnly, UID.JPEGLossless, UID.JPEGLSLossless })
    public void ybr422Raw(String tsuid) throws Exception {
        double result = raw2LosslessCompressed("YBR_422-Raw-Subsample.dcm", tsuid, true);
        assertTrue(result < 1.0, "Image content of the input and the output have significant differences");
    }

    private double raw2LosslessCompressed(String ifname, String dstTsuid, boolean fmi) throws Exception {
        File in = new File(IN_DIR, ifname);
        File out = addFileSuffix(getDestinationFile(in, OUT_DIR), dstTsuid);
        Transcoder.dcm2dcm(in.getPath(), out.getPath(), dstTsuid);

        // TODO implement http://qtandopencv.blogspot.com/2016/06/introduction-to-image-hash-module-of.html
        
        // try (DicomImageReader readerIn = new DicomImageReader(Transcoder.dicomImageReaderSpi);
        // DicomImageReader readerOut = new DicomImageReader(Transcoder.dicomImageReaderSpi)) {
        // readerIn.setInput(new DicomFileInputStream(in));
        // readerOut.setInput(new DicomFileInputStream(out));
        // List<PlanarImage> imagesIn = readerIn.getPlanarImages(null);
        // List<PlanarImage> imagesOut = readerIn.getPlanarImages(null);
        //
        // }
        return 0.0;
    }

    private static File getDestinationFile(File src, File dst) {
        String baseDir;
        String filename;
        if (dst.isDirectory()) {
            baseDir = dst.getPath();
            filename = src.getName();
        } else {
            baseDir = dst.getParent();
            filename = dst.getName();
        }
        return new File(baseDir, filename);
    }

    private static File addFileSuffix(File file, String suffix) {
        if (suffix == null) {
            return file;
        }
        String path = file.getPath();
        int i = path.lastIndexOf('.');
        if (i > 0) {
            return new File(path.substring(0, i) + "-" + suffix + path.substring(i));
        }
        return new File(file.getPath() + suffix);
    }
}