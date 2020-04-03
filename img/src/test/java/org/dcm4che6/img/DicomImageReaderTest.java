package org.dcm4che6.img;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
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
    static final Path IN_DIR = Path.of(DicomImageReaderTest.class.getResource("").getFile());
    static DicomImageReader reader;

    @BeforeAll
    static void setUp() {
        BasicConfigurator.configure();
        reader = (DicomImageReader) ImageIO.getImageReadersByFormatName("DICOM").next();
    }

    @AfterAll
    static void tearDown() {
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
        // Hash content comparison http://qtandopencv.blogspot.com/2016/06/introduction-to-image-hash-module-of.html
        assertEquals(19, imagesIn.size(),
            "The number of image frames doesn't match");
    }

    
//    @Test
//    public void compressed_multiframe_multifragments() throws Exception {
//        List<PlanarImage> imagesIn = readDicomImage("35302201-jpls-pkb1k.dcm");
//        // Hash content comparison http://qtandopencv.blogspot.com/2016/06/introduction-to-image-hash-module-of.html
//        assertEquals(19, imagesIn.size(),
//            "The number of image frames of the input file is different of the output file");
//    }
}
