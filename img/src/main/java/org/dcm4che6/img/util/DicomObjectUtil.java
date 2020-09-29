package org.dcm4che6.img.util;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.img.data.CIELab;
import org.dcm4che6.util.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DicomObjectUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger( DicomObjectUtil.class );

    private DicomObjectUtil(){
    }

    public static DicomObject getNestedDataset(DicomObject dicom, int tagSeq) {
        return getNestedDataset(dicom, tagSeq, 0);
    }

    public static DicomObject getNestedDataset(DicomObject dicom, int tagSeq, int index) {
        if (dicom != null) {
            Optional<DicomElement> item = dicom.get(tagSeq);
            if (item.isPresent() && !item.get().isEmpty()) {
                return item.get().getItem(index);
            }
        }
        return null;
    }

    public static List<DicomObject> getSequence(DicomObject dicom, int tagSeq) {
        if (dicom != null) {
            Optional<DicomElement> item = dicom.get(tagSeq);
            if (item.isPresent() && !item.get().isEmpty() && item.get().vr() == VR.SQ) {
                return item.get().itemStream().collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    public static boolean isImageFrameApplicableToReferencedImageSequence(List<DicomObject> refImgSeq, int childTag, String sopInstanceUID, int frame, boolean required) {
        if (!required && (refImgSeq == null || refImgSeq.isEmpty())) {
            return true;
        }
        if (StringUtil.hasText(sopInstanceUID)) {
            for (DicomObject sopUID : refImgSeq) {
                if (sopInstanceUID.equals(sopUID.getString(Tag.ReferencedSOPInstanceUID).orElse(null))) {
                    int[] frames = sopUID.getInts(childTag).orElse(null);
                    if (frames == null || frames.length == 0) {
                        return true;
                    }
                    for (int f : frames) {
                        if (f == frame) {
                            return true;
                        }
                    }
                    // if the frame has been excluded
                    return false;
                }
            }
        }
        return false;
    }

    public static LocalDate getDicomDate(String date) {
        if (StringUtil.hasText(date)) {
            try {
                return DateTimeUtils.parseDA(date);
            } catch (Exception e) {
                LOGGER.error("Parse DICOM date", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    public static LocalTime getDicomTime(String time) {
        if (StringUtil.hasText(time)) {
            try {
                return DateTimeUtils.parseTM(time);
            } catch (Exception e1) {
                LOGGER.error("Parse DICOM time", e1); //$NON-NLS-1$
            }
        }
        return null;
    }

    public static LocalDateTime dateTime(DicomObject dcm, int dateID, int timeID)  {
        if (dcm == null) {
            return null;
        }
        LocalDate date = getDicomDate(dcm.getString(dateID).orElse(null));
        if (date == null) {
            return null;
        }
        LocalTime time =  getDicomTime(dcm.getString(timeID).orElse(null));
        if (time == null) {
            return date.atStartOfDay();
        }
        return LocalDateTime.of(date, time);
    }

    public static void copyDataset(DicomObject dicom, DicomObject copy) {
        dicom.forEach(i -> {
            if (i.vr() == VR.SQ) {
                DicomElement seq = copy.newDicomSequence(i.tag());
                i.itemStream().forEach(d -> {
                    DicomObject c = DicomObject.newDicomObject();
                    copyDataset(d, c);
                    seq.addItem(c);
                });
            } else {
                copy.add(i);
            }
        });
    }

    /**
     * Build the shape from DICOM Shutter
     *
     * @see <a href="http://dicom.nema.org/MEDICAL/DICOM/current/output/chtml/part03/sect_C.7.6.11.html">C.7.6.11
     *      Display Shutter Module</a>
     * @param dcm
     */
    public static Area getShutterShape(DicomObject dcm) {
        Area shape = null;
        String shutterShape = dcm.getString(Tag.ShutterShape).orElse(null);
        if (shutterShape != null) {
            if (shutterShape.contains("RECTANGULAR") || shutterShape.contains("RECTANGLE")) { //$NON-NLS-1$ //$NON-NLS-2$
                Rectangle2D rect = new Rectangle2D.Double();
                rect.setFrameFromDiagonal(
                        dcm.getInt(Tag.ShutterLeftVerticalEdge).orElse(0),
                        dcm.getInt(Tag.ShutterUpperHorizontalEdge).orElse(0),
                        dcm.getInt(Tag.ShutterRightVerticalEdge).orElse(0),
                        dcm.getInt(Tag.ShutterLowerHorizontalEdge).orElse(0));
                shape = new Area(rect);

            }
            if (shutterShape.contains("CIRCULAR")) { //$NON-NLS-1$
                int[] centerOfCircularShutter = dcm.getInts(Tag.CenterOfCircularShutter).orElse(null);
                if (centerOfCircularShutter != null && centerOfCircularShutter.length >= 2) {
                    Ellipse2D ellipse = new Ellipse2D.Double();
                    double radius = dcm.getInt(Tag.RadiusOfCircularShutter).orElse(0);
                    // Thanks DICOM for reversing x,y by row,column
                    ellipse.setFrameFromCenter(centerOfCircularShutter[1], centerOfCircularShutter[0],
                            centerOfCircularShutter[1] + radius, centerOfCircularShutter[0] + radius);
                    if (shape == null) {
                        shape = new Area(ellipse);
                    } else {
                        shape.intersect(new Area(ellipse));
                    }
                }
            }
            if (shutterShape.contains("POLYGONAL")) { //$NON-NLS-1$
                int[] points = dcm.getInts(Tag.VerticesOfThePolygonalShutter).orElse(null);
                if (points != null) {
                    Polygon polygon = new Polygon();
                    for (int i = 0; i < points.length / 2; i++) {
                        // Thanks DICOM for reversing x,y by row,column
                        polygon.addPoint(points[i * 2 + 1], points[i * 2]);
                    }
                    if (shape == null) {
                        shape = new Area(polygon);
                    } else {
                        shape.intersect(new Area(polygon));
                    }
                }
            }
        }
        return shape;
    }

    public static Color getShutterColor(DicomObject dcm) {
        int[] rgb = CIELab.dicomLab2rgb(dcm.getInts(Tag.ShutterPresentationColorCIELabValue).orElse(null));
        return  getRGBColor(dcm.getInt(Tag.ShutterPresentationValue).orElse(0xFFFF), rgb);
    }

    public static Color getRGBColor(int pGray, int[] rgbColour) {
        int r, g, b;
        if (rgbColour != null && rgbColour.length >= 3) {
            r = rgbColour[0];
            g = rgbColour[1];
            b = rgbColour[2];
            if (r > 255) {
                r >>= 8;
            }
            if (g > 255) {
                g >>= 8;
            }
            if (b > 255) {
                b >>= 8;
            }
        } else {
            r = g = b = pGray > 255 ? pGray >> 8 : pGray;
        }
        r &= 0xFF;
        g &= 0xFF;
        b &= 0xFF;
        int conv = (r << 16) | (g << 8) | b | 0x1000000;
        return new Color(conv);
    }
}
