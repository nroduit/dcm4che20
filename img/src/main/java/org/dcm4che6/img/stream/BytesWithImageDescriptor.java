package org.dcm4che6.img.stream;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2018
 */
public interface BytesWithImageDescriptor extends ImageReaderDescriptor {
    ByteBuffer getBytes(int frame) throws IOException;

    String getTransferSyntax();

    boolean forceYbrToRgbConversion();
}
