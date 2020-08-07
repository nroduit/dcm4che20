package org.dcm4che6.img.op;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageProcessor;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import java.util.Objects;

public class MaskArea {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaskArea.class);
    private final Color color;
    private final List<Shape> shapeList;

    public MaskArea(List<Shape> shapeList) {
        this(shapeList, null);
    }

    public MaskArea(List<Shape> shapeList, Color color) {
        this.shapeList = Objects.requireNonNull(shapeList);
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public List<Shape> getShapeList() {
        return shapeList;
    }

    public static ImageCV drawShape(Mat srcImg, MaskArea maskArea) {
        if (maskArea != null && !maskArea.getShapeList().isEmpty()) {
            Color c = maskArea.getColor();
            ImageCV dstImg = new ImageCV();
            srcImg.copyTo(dstImg);
            Scalar color = c == null ? new Scalar(0, 0, 0) : new Scalar(c.getBlue(), c.getGreen(), c.getRed());
            for (Shape shape : maskArea.getShapeList()) {
                if (c == null && shape instanceof Rectangle) {
                    Rectangle r = (Rectangle) shape;
                    r = r.intersection(new Rectangle(0, 0, srcImg.width(), srcImg.height()));
                    Rect rect2d = new Rect(r.x, r.y, r.width, r.height);
                    if (r.width < 3 || r.height < 3) {
                        LOGGER.warn("The masking shape is not applicable: {}", r.toString());
                    } else {
                        Imgproc.blur(srcImg.submat(rect2d), dstImg.submat(rect2d), new Size(7, 7));
                    }
                } else {
                    List<MatOfPoint> pts = ImageProcessor.transformShapeToContour(shape, true);
                    Imgproc.fillPoly(dstImg, pts, color);
                }
            }
            return dstImg;
        }
        return ImageCV.toImageCV(srcImg);
    }
}
