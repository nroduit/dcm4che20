package org.dcm4che6.img.data;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmbeddedOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger( EmbeddedOverlay.class );

    private final int groupOffset;
    private final int bitPosition;

    public EmbeddedOverlay(int groupOffset, int bitPosition) {
        this.groupOffset = groupOffset;
        this.bitPosition = bitPosition;
    }

    public int getBitPosition() {
        return bitPosition;
    }

    public int getGroupOffset() {
        return groupOffset;
    }

    public static List<EmbeddedOverlay> getEmbeddedOverlay(DicomObject dcm) {
        List<EmbeddedOverlay> data = new ArrayList<>();
        int bitsAllocated = dcm.getInt(Tag.BitsAllocated).orElse(8);
        int bitsStored = dcm.getInt(Tag.BitsStored).orElse(bitsAllocated);
        for (int i = 0; i < 16; i++) {
            int gg0000 = i << 17;
            if (dcm.getInt(Tag.OverlayBitsAllocated | gg0000).orElse(1) != 1) {
                int bitPosition = dcm.getInt(Tag.OverlayBitPosition | gg0000).orElse(0);
                if (bitPosition < bitsStored) {
                    LOGGER.info("Ignore embedded overlay #{} from bit #{} < bits stored: {}",
                            (gg0000 >>> 17) + 1, bitPosition, bitsStored);
                } else {
                    data.add(new EmbeddedOverlay(gg0000, bitPosition));
                }
            }
        }
        return data.isEmpty() ? Collections.emptyList() : data;
    }

}
