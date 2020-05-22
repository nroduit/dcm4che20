package org.dcm4che6.img;

import org.dcm4che6.data.Implementation;
import org.dcm4che6.img.stream.BytesWithImageDescriptor;
import org.dcm4che6.img.stream.DicomFileInputStream;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class DicomImageReaderSpi extends ImageReaderSpi {

    private static final String[] dicomFormatNames = { "dicom", "DICOM" };
    private static final String[] dicomExt = { "dcm", "dic", "dicm", "dicom" };
    private static final String[] dicomMimeType = { "application/dicom" };
    private static final Class<?>[] dicomInputTypes = { DicomFileInputStream.class, BytesWithImageDescriptor.class };

    public DicomImageReaderSpi() {
        super("dcm4che", Implementation.VERSION_NAME, dicomFormatNames, dicomExt, dicomMimeType,
                DicomImageReader.class.getName(), dicomInputTypes,
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
        return "DICOM Image Reader (dcm4che)";
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
