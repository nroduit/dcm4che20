package org.dcm4che6.img;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Polygon;
import java.awt.Shape;
import java.io.File;
import java.text.DecimalFormat;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.dcm4che6.img.stream.DicomFileInputStream;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.img.util.MathUtil;
import org.dcm4che6.img.util.StringUtil;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

class ImageRenderingTest {

    @Test
    void getModalatyLutImage_Statistics() throws Exception {
        File in = new File(TranscoderTest.IN_DIR, "Mono2-CT-16.dcm");
        DicomImageReadParam readParam = new DicomImageReadParam();
        Polygon polygon = new Polygon();
        polygon.addPoint(150, 200);
        polygon.addPoint(200, 190);
        polygon.addPoint(200, 250);
        polygon.addPoint(140, 240);
        double[][] val = statistics(in.getPath(), readParam, polygon);
        
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
            int nbFrames = reader.getImageDescriptor().getFrames();
            for (int i = 0; i < nbFrames; i++) {
                PlanarImage img = reader.getPlanarImage(i, params);
                ImageDescriptor desc = reader.getImageDescriptor();
                img = ImageRendering.getModalityLutImage(img, desc, params);

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