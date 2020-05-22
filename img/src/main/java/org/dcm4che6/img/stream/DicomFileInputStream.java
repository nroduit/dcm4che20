package org.dcm4che6.img.stream;

import org.dcm4che6.img.DicomMetaData;
import org.dcm4che6.io.DicomInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Nicolas Roduit
 *
 */
public class DicomFileInputStream extends DicomInputStream implements ImageReaderDescriptor {

    private final Path path;
    private DicomMetaData metadata;


    public DicomFileInputStream(Path path) throws IOException  {
        super(Files.newInputStream(path));
        this.path = path;
    }

    public DicomFileInputStream(String path) throws IOException {
        this(Path.of(path));
    }

    public Path getPath() {
        return path;
    }

    public DicomMetaData getMetadata() throws IOException {
        if (metadata == null) {
            this.metadata = new DicomMetaData(this);
        }
        return metadata;
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
        try {
            getMetadata();
        } catch (IOException e) {
            return null;
        }
        return metadata.getImageDescriptor();
    }
}
