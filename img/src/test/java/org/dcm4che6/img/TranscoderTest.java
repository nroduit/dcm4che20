package org.dcm4che6.img;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.data.UID;
import org.dcm4che6.img.Transcoder.Format;
import org.dcm4che6.img.data.ImageContentHash;
import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.stream.DicomFileInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class TranscoderTest {
    static final Path IN_DIR = Path.of(TranscoderTest.class.getResource("").getFile());
    static final Path OUT_DIR = Path.of("target/test-out/");

    private static DicomImageReader reader;

    private final Consumer<Double> zeroDiff = val -> assertTrue(val == 0.0, "The hash result of the image input is not exactly the same as the output image");
    private final Consumer<Double> hasDiff = val -> assertTrue(val != 0.0, "The hash result of the image input is exactly the same as the output image");

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        BasicConfigurator.configure();
        FileUtil.delete(OUT_DIR);
        Files.createDirectories(OUT_DIR);
        reader = (DicomImageReader) ImageIO.getImageReadersByFormatName("DICOM").next();
    }

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
        if (reader != null) {
            reader.dispose();
        }
    }

    @Test
    public void dcm2image_ApllyPresentationStateLUT() throws Exception {
        Path in = Path.of(IN_DIR.toString(), "imageForPrLUTs.dcm");
        Path inPr = Path.of(IN_DIR.toString(), "prLUTs.dcm");
        DicomImageReadParam readParam = new DicomImageReadParam();
        readParam.setPresentationState(PrDicomObject.getPresentationState(inPr.toString()));
        ImageTranscodeParam params = new ImageTranscodeParam(readParam, Format.PNG);
        List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);
        assertFalse(outFiles.isEmpty());

        Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
        enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
        enumMap.put(ImageContentHash.PHASH, zeroDiff);
        enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
        enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);
        compareImageContent(Path.of(IN_DIR.toString(), "expected_imgForPrLUT.png"), outFiles.get(0), enumMap);
    }

    @Test
    public void dcm2image_ApllyPresentationStateOverlay() throws Exception {
        Path in = Path.of(IN_DIR.toString(), "overlay.dcm");
        Path inPr = Path.of(IN_DIR.toString(), "prOverlay.dcm");
        DicomImageReadParam readParam = new DicomImageReadParam();
        readParam.setPresentationState(PrDicomObject.getPresentationState(inPr.toString()));
        readParam.setOverlayColor(Color.GREEN);
        ImageTranscodeParam params = new ImageTranscodeParam(readParam, Format.PNG);
        List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);
        assertFalse(outFiles.isEmpty());

        Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
        enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
        enumMap.put(ImageContentHash.PHASH, zeroDiff);
        enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
        enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);
        compareImageContent(Path.of(IN_DIR.toString(), "expected_overlay.png"), outFiles.get(0), enumMap);
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    public void dcm2image_YBR422Raw(Format format) throws Exception {
        Path in = Path.of(IN_DIR.toString(), "ybr422-raw.dcm");
        ImageTranscodeParam params = new ImageTranscodeParam(format);
        List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);

        assertFalse(outFiles.isEmpty());

        Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
        enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
        enumMap.put(ImageContentHash.PHASH, zeroDiff);
        compareImageContent(in, outFiles, enumMap);
    }

    @ParameterizedTest
    @ValueSource(strings = { UID.JPEG2000, UID.JPEGBaseline1, UID.JPEGLSLossyNearLossless })
    public void dcm2dcm_YBR422Raw_Lossy(String tsuid) throws Exception {
        Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
        enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
        enumMap.put(ImageContentHash.PHASH, zeroDiff);
        enumMap.put(ImageContentHash.COLOR_MOMENT, hasDiff); // JPEG compression mainly reduce the color information

        DicomTranscodeParam params = new DicomTranscodeParam(tsuid);
        if (tsuid == UID.JPEGLSLossyNearLossless) {
            params.getWriteJpegParam().setNearLosslessError(3);
        } else {
            params.getWriteJpegParam().setCompressionQuality(80);
        }
        transcodeDicom("ybr422-raw.dcm", params, enumMap);
    }

    @Test
    public void dcm2dcm_Resize() throws Exception {
        DicomTranscodeParam params = new DicomTranscodeParam(UID.JPEGLSLossyNearLossless);
        params.getReadParam().setSourceRenderSize(new Dimension(128, 128));
        Path out = transcodeDicom("signed-raw-9bit.dcm", params, null);
        List<PlanarImage> imgs = readImages(out);

        assertEquals(128, imgs.get(0).width(), "The width of image doesn't match");
        assertEquals(128, imgs.get(0).height(), "The height of image doesn't match");
    }

    @ParameterizedTest
    @ValueSource(strings = { UID.JPEG2000LosslessOnly, UID.JPEGLossless, UID.JPEGLSLossless })
    public void dcm2dcm_YBR422Raw_Lossless(String tsuid) throws Exception {
        Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
        // The image content must be fully preserved with lossless compression
        enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
        enumMap.put(ImageContentHash.PHASH, zeroDiff);
        enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
        enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);

        DicomTranscodeParam params = new DicomTranscodeParam(tsuid);
        transcodeDicom("ybr422-raw.dcm", params, enumMap);
    }

    private void compareImageContent(Path in, Path out, Map<ImageContentHash, Consumer<Double>> enumMap) throws Exception {
        compareImageContent(in, List.of(out), enumMap);
    }

    private void compareImageContent(Path in, List<Path> outFiles, Map<ImageContentHash, Consumer<Double>> enumMap)
        throws Exception {
        List<PlanarImage> imagesIn = readImages(in);
        List<PlanarImage> imagesOut = readImages(outFiles);

        assertTrue(imagesIn.size() == imagesOut.size(),
            "The number of image frames of the input file is different of the output file");

        for (int i = 0; i < imagesIn.size(); i++) {
            PlanarImage imgIn = imagesIn.get(i);
            PlanarImage imgOut = imagesOut.get(i);

            System.out.println("");
            System.out.println("=== Image content diff of image " + (i + 1));
            System.out.println("=== Input: " + in);
            System.out.println("=== Output: " + (i >= outFiles.size() ? outFiles.get(i) : outFiles.get(0)));

            for (Entry<ImageContentHash, Consumer<Double>> map : enumMap.entrySet()) {
                // Hash content comparison http://qtandopencv.blogspot.com/2016/06/introduction-to-image-hash-module-of.html
                double val = map.getKey().compare(imgIn.toMat(), imgOut.toMat());
                System.out.println("\t" + map.getKey().name() + ": " + val);
                map.getValue().accept(val);
            }
        }
    }

    private List<PlanarImage> readImages(List<Path> files) throws IOException {
        if (files.size() == 1 && files.get(0).getFileName().toString().endsWith(".dcm")) {
            reader.setInput(new DicomFileInputStream(files.get(0)));
            return reader.getPlanarImages(null);
        } else {
            return files.stream().map(p -> ImageProcessor.readImageWithCvException(p.toFile()))
                .collect(Collectors.toList());
        }
    }

    private List<PlanarImage> readImages(Path path) throws IOException {
        if (path.getFileName().toString().endsWith(".dcm")) {
            reader.setInput(new DicomFileInputStream(path));
            return reader.getPlanarImages(null);
        } else {
            return List.of(ImageProcessor.readImageWithCvException(path.toFile()));
        }
    }

    private Path transcodeDicom(String ifname, DicomTranscodeParam params, Map<ImageContentHash, Consumer<Double>> enumMap)
        throws Exception {
        Path in = Path.of(IN_DIR.toString(), ifname);
        Path out = Path.of(OUT_DIR.toString(), params.getOutputTsuid());
        Files.createDirectories(out);
        out = Transcoder.dcm2dcm(in, out, params);

        assertTrue(out != null && Files.size(out) > 0, "The ouput image is empty");
        if (enumMap != null) {
            compareImageContent(in, out, enumMap);
        }
        return out;
    }

}
