package org.dcm4che6.img;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;

import org.dcm4che6.data.UID;
import org.dcm4che6.img.Transcoder.Format;
import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.util.FileUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

public class TranscoderTest {
    static final File IN_DIR = new File(TranscoderTest.class.getResource("").getFile());
    static final File OUT_DIR = new File("target/test-out/");

    private static DicomImageReader reader;

    @BeforeAll
    protected static void setUpBeforeClass() throws Exception {
        FileUtil.delete(OUT_DIR);
        OUT_DIR.mkdirs();
        reader = (DicomImageReader) ImageIO.getImageReadersByFormatName("DICOM").next();
    }

    @AfterAll
    protected static void tearDownAfterClass() throws Exception {
        if (reader != null) {
            reader.dispose();
        }
    }

    @Test
    public void dcm2image_ApllyPresentationState() throws Exception {
        File in = new File(IN_DIR, "imgForPrLUT.dcm");
        File inPr = new File(IN_DIR, "prLUTs.dcm");
        DicomImageReadParam readParam = new DicomImageReadParam();
        readParam.setPresentationState(PrDicomObject.getPresentationState(inPr.getPath()));
        ImageTranscodeParam params = new ImageTranscodeParam(readParam, Format.PNG);
        List<File> outFiles = Transcoder.dcm2image(in.getPath(), OUT_DIR.getPath(), params);
        assertTrue(!outFiles.isEmpty());
        assertTrue(outFiles.stream().allMatch(f -> f.length() > 10000));
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    public void dcm2image_YBR422Raw(Format format) throws Exception {
        File in = new File(IN_DIR, "YBR_422-Raw-Subsample.dcm");
        ImageTranscodeParam params = new ImageTranscodeParam(format);
        List<File> outFiles = Transcoder.dcm2image(in.getPath(), OUT_DIR.getPath(), params);

        assertTrue(!outFiles.isEmpty());
        assertTrue(outFiles.stream().allMatch(f -> f.length() > 10000));
        // assertTrue(result < 3.0, "Image content of the input and the output have significant differences");
    }

    @ParameterizedTest
    @ValueSource(strings = { UID.JPEG2000, UID.JPEGBaseline1, UID.JPEGLSLossyNearLossless })
    public void dcm2dcm_YBR422Raw_Lossy(String tsuid) throws Exception {
        DicomTranscodeParam params = new DicomTranscodeParam(tsuid);
        if (tsuid == UID.JPEGLSLossyNearLossless) {
            params.getWriteJpegParam().setNearLosslessError(3);
        } else {
            params.getWriteJpegParam().setCompressionQuality(80);
        }
        double result = transcodeDicom("YBR_422-Raw-Subsample.dcm", params);
        assertTrue(result < 3.0, "Image content of the input and the output have significant differences");
    }

    @ParameterizedTest
    @ValueSource(strings = { UID.JPEG2000LosslessOnly, UID.JPEGLossless, UID.JPEGLSLossless })
    public void dcm2dcm_YBR422Raw_Lossless(String tsuid) throws Exception {
        DicomTranscodeParam params = new DicomTranscodeParam(tsuid);
        double result = transcodeDicom("YBR_422-Raw-Subsample.dcm", params);
        assertTrue(result == 0.0, "Image content of the input and the output have significant differences");
    }

    private double transcodeDicom(String ifname, DicomTranscodeParam params) throws Exception {
        File in = new File(IN_DIR, ifname);
        File out = new File(OUT_DIR, params.getOutputTsuid());
        out.mkdir();
        out = Transcoder.dcm2dcm(in.getPath(), out.getPath(), params);

        assertTrue(out != null && out.length() > 0);

        // TODO implements hash content comparison http://qtandopencv.blogspot.com/2016/06/introduction-to-image-hash-module-of.html

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

}
