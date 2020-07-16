package org.dcm4che6.img.op;

import java.awt.*;
import java.util.List;
import java.util.Objects;

public class MaskArea {
    private final Color color;
    private final List<Shape> shapeList;

    public MaskArea(Color color, List<Shape> shapeList) {
        this.color = Objects.requireNonNull(color);
        this.shapeList = Objects.requireNonNull(shapeList);
    }

    public Color getColor() {
        return color;
    }

    public List<Shape> getShapeList() {
        return shapeList;
    }
}
