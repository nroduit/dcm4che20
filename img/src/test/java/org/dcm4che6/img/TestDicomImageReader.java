package org.dcm4che6.img;



import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
/**
 * @author Nicolas Roduit
 *
 */
public class TestDicomImageReader {

    static DicomImageReader reader;

    @BeforeAll
    static void setUp() {
        reader = (DicomImageReader) ImageIO.getImageReadersByFormatName("DICOM").next();
    }

    @AfterAll
    static void tearDown() {
        if (reader != null)
            reader.dispose();
    }
    

}
