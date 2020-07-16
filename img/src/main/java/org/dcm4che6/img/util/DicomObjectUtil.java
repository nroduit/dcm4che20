package org.dcm4che6.img.util;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.VR;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DicomObjectUtil {

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
}
