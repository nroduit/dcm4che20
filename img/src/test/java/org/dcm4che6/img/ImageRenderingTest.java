package org.dcm4che6.img;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Polygon;
import java.awt.Shape;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.dcm4che6.img.stream.DicomFileInputStream;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.junit.jupiter.api.Test;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class ImageRenderingTest {

    @Test
    public void getModalityLutImage_Statistics() throws Exception {
        Path in = Path.of(TranscoderTest.IN_DIR.toString(), "mono2-CT-16bit.dcm");
        DicomImageReadParam readParam = new DicomImageReadParam();
        Polygon polygon = new Polygon();
        polygon.addPoint(150, 200);
        polygon.addPoint(200, 190);
        polygon.addPoint(200, 250);
        polygon.addPoint(140, 240);
        double[][] val = statistics(in.toString(), readParam, polygon);
        
        assertTrue(val != null);
        assertTrue(val[0][0] == -202.0);
        assertTrue(val[1][0] == 961.0);
        assertTrue(MathUtil.isEqual(val[2][0], 13.184417441029307));
        assertTrue(MathUtil.isEqual(val[3][0], 146.3726881826613));
    }

    public static double[][] statistics(String srcPath, DicomImageReadParam params, Shape shape) throws Exception {
        if (!StringUtil.hasText(srcPath)) {
            throw new IllegalStateException("Path cannot be empty");
        }
        try (DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi)) {
            reader.setInput(new DicomFileInputStream(srcPath));
            ImageDescriptor desc = reader.getImageDescriptor();
            for (int i = 0; i < desc.getFrames(); i++) {
                PlanarImage img = reader.getPlanarImage(i, params);
                img = ImageRendering.getModalityLutImage(img, desc, params, i);

                double[][] val = ImageProcessor.meanStdDev(img.toMat(), shape, desc.getPixelPaddingValue(),
                    desc.getPixelPaddingRangeLimit());
                if (val != null) {
                    DecimalFormat df = new DecimalFormat("#.00");
                    StringBuilder b = new StringBuilder("Image path: ");
                    b.append(srcPath);
                    b.append("\nPixel statistics of real values:");
                    b.append("\n\tMin: ");
                    b.append(DoubleStream.of(val[0]).mapToObj(df::format).collect(Collectors.joining(" ")));
                    b.append("\n\tMax: ");
                    b.append(DoubleStream.of(val[1]).mapToObj(df::format).collect(Collectors.joining(" ")));
                    b.append("\n\tMean: ");
                    b.append(DoubleStream.of(val[2]).mapToObj(df::format).collect(Collectors.joining(" ")));
                    b.append("\n\tStd: ");
                    b.append(DoubleStream.of(val[3]).mapToObj(df::format).collect(Collectors.joining(" ")));
                    System.out.print(b.toString());
                }
                return val;
            }
        }
        return null;
    }
}