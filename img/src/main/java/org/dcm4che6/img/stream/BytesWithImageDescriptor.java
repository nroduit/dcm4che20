package org.dcm4che6.img.stream;

import org.dcm4che6.data.DicomObject;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2018
 */
public interface BytesWithImageDescriptor extends ImageReaderDescriptor {
    ByteBuffer getBytes(int frame) throws IOException;

    String getTransferSyntax();

    DicomObject getPaletteColorLookupTable();
}
