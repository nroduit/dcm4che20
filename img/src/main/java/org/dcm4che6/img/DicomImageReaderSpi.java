package org.dcm4che6.img;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che6.data.Implementation;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class DicomImageReaderSpi extends ImageReaderSpi {

    private static final String vendorName = "org.dcm4che";
    private static final String[] formatNames = { "dicom", "DICOM" };
    private static final String[] suffixes = { "dcm", "dic", "dicm", "dicom" };
    private static final String[] MIMETypes = { "application/dicom" };
    private static final Class<?>[] inputTypes = { ImageInputStream.class, InputStream.class, DicomMetaData.class };

    public DicomImageReaderSpi() {
        super(vendorName, Implementation.VERSION_NAME, formatNames, suffixes, MIMETypes, 
                DicomImageReader.class.getName(), inputTypes,
                null,  // writerSpiNames
                false, // supportsStandardStreamMetadataFormat
                null,  // nativeStreamMetadataFormatName
                null,  // nativeStreamMetadataFormatClassName
                null,  // extraStreamMetadataFormatNames
                null,  // extraStreamMetadataFormatClassNames
                false, // supportsStandardImageMetadataFormat
                null,  // nativeImageMetadataFormatName
                null,  // nativeImageMetadataFormatClassName
                null,  // extraImageMetadataFormatNames
                null); // extraImageMetadataFormatClassNames
    }

    @Override
    public String getDescription(Locale locale) {
        return "DICOM Image Reader";
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        ImageInputStream iis = (ImageInputStream) source;
        iis.mark();
        try {
            int tag = iis.read()
                   | (iis.read()<<8)
                   | (iis.read()<<16)
                   | (iis.read()<<24);
            return ((tag >= 0x00080000 && tag <= 0x00080016)
                  || (iis.skipBytes(124) == 124
                   && iis.read() == 'D'
                   && iis.read() == 'I'
                   && iis.read() == 'C'
                   && iis.read() == 'M'));
        } finally {
            iis.reset();
        }
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
        return new DicomImageReader(this);
    }

}
