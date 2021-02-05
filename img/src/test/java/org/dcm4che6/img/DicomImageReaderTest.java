package org.dcm4che6.img;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.img.stream.DicomFileInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.data.PlanarImage;

/**
 * @author Nicolas Roduit
 *
 */
public class DicomImageReaderTest {
    static Path IN_DIR;
    static DicomImageReader reader;

    @BeforeAll
    public static void setUp() throws URISyntaxException {
        IN_DIR = Paths.get(DicomImageReaderTest.class.getResource("").toURI());
        BasicConfigurator.configure();
        reader = (DicomImageReader) ImageIO.getImageReadersByFormatName("DICOM").next();
    }

    @AfterAll
    public static void tearDown() {
        if (reader != null)
            reader.dispose();
    }

    private List<PlanarImage> readDicomImage(String filename) throws IOException   {
            reader.setInput(new DicomFileInputStream(Path.of(IN_DIR.toString(), filename)));
            return reader.getPlanarImages(null);
    }

    @Test
    public void jpeg2000_lossy_multiframe_multifragments() throws Exception {
        List<PlanarImage> imagesIn = readDicomImage("jpeg2000-multiframe-multifragments.dcm");
        assertEquals(19, imagesIn.size(), "The number of image frames doesn't match");
        for (PlanarImage img : imagesIn) {
            assertEquals(256, img.width(), "The image size doesn't match");
        }
    }


    @Test
    public void ybrFull_RLE() throws Exception {
        List<PlanarImage> imagesIn = readDicomImage("ybrFull-RLE.dcm");
        assertEquals(1, imagesIn.size(), "The number of image frames doesn't match");
        for (PlanarImage img : imagesIn) {
            assertEquals(640, img.width(), "The image width doesn't match");
            assertEquals(480, img.height(), "The image height doesn't match");
        }
    }
}
