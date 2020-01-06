package org.dcm4che6.img;

import java.awt.Polygon;
import java.io.File;
import java.io.FileInputStream;
import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.dcm4che6.img.Transcoder.Format;
import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.stream.DicomFileInputStream;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.img.util.StringUtil;
import org.dcm4che6.io.DicomInputStream;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class ImageTest {

    public static void main(String[] args) throws Exception {
        List<Format> formats = List.of(Format.JPEG, Format.PNG, Format.TIF, Format.PNM, Format.BMP, Format.HDR);
        DicomImageReadParam params = new DicomImageReadParam();
        // params.put(RenderingParams.JEPG_COMPRESSION_RATIO, 100);
        // params.put(RenderingParams.APLLY_WL, false);
        // String srcPath = "/home/nicolas/Images/ImagesTest/DICOM/LUT/JPEG_LossyYBR.dcm";

        // String srcPath = "/home/nicolas/Images/out/gray-12-bit-gdcm-90.dcm";
        String srcPath =
            "/home/nicolas/Images/ImagesTest/DICOM/TEST-Demo/TEST, Compression/08.12.2003/[1] MR  -- (1 instances)/1.2.40.0.13.1.1.136753961308387839079969956438371041433";
        // String srcPath = "/home/nicolas/Images/out/aw-palette.dcm";
        String dstPath = "/home/nicolas/Images/out/test";
        // Transcoder.dcm2dcm(srcPath, dstPath, UID.JPEGLossless);
          Transcoder.dcm2image(srcPath, dstPath, Format.PNG, params);
     //    convertDirectory(new File(srcPath), new File(dstPath), params, formats, false);

     //   statistics(srcPath, params);

        // String srcDir = "/home/nicolas/Images/ImagesTest/DICOM/TEST-Demo";
        // convertDirectory(new File(srcDir), new File(dstPath), params, List.of(Format.PNG), true);
        String prPath = "/home/nicolas/Images/ImagesTest/DICOM/TEST-Demo/issues/prLUTs.dcm";
        String srcPath2 = "/home/nicolas/Images/ImagesTest/DICOM/TEST-Demo/issues/imgForPrLUT.dcm";
        DicomImageReadParam params2 = new DicomImageReadParam();
        params2.setPresentationState(getPresentationState(prPath));
        Transcoder.dcm2image(srcPath2, dstPath, Format.PNG, params2);
    }

    private static PrDicomObject getPresentationState(String prPath) throws Exception {
        try (DicomInputStream dis = new DicomInputStream(new FileInputStream(prPath))) {
            return new PrDicomObject(dis.readDataSet());
        }
    }

    public static void statistics(String srcPath, DicomImageReadParam params) throws Exception {
        if (!StringUtil.hasText(srcPath)) {
            throw new IllegalStateException("Path cannot be empty");
        }
        try (DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi)) {
            reader.setInput(new DicomFileInputStream(srcPath));
            int nbFrames = reader.getImageDescriptor().getFrames();
            for (int i = 0; i < nbFrames; i++) {
                PlanarImage img = reader.getPlanarImage(i, params);
                ImageDescriptor desc = reader.getImageDescriptor();
                img = ImageRendering.getModalatyLutImage(img, desc, params);
                Polygon polygon = new Polygon();
                polygon.addPoint(0, 0);
                polygon.addPoint(150, 0);
                polygon.addPoint(150, 150);
                polygon.addPoint(0, 150);
                double[][] val = ImageProcessor.meanStdDev(img.toMat(), polygon, desc.getPixelPaddingValue(),
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
            }
        }
    }

    private static void transcodeAll(String srcPath, String dstPath, DicomImageReadParam params,
        List<Format> formats) {
        formats.forEach(f -> {
            try {
                Transcoder.dcm2image(srcPath, dstPath, f, params);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void convertDirectory(File srcDir, File baseDstDir, DicomImageReadParam params,
        List<Format> formats, boolean recursive) {
        File[] fList = srcDir.listFiles();
        if (!baseDstDir.exists()) {
            baseDstDir.mkdirs();
        }
        for (File f : fList) {
            if (f.isFile()) {
                transcodeAll(f.getPath(), baseDstDir.getPath(), params, formats);
            } else if (recursive && f.isDirectory()) {
                convertDirectory(f, new File(baseDstDir, f.getName()), params, formats, recursive);
            }
        }
    }
}
