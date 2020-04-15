package org.dcm4che6.img;

import java.io.IOException;
import java.util.Objects;

import javax.imageio.metadata.IIOMetadata;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.io.DicomInputStream;
import org.w3c.dom.Node;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class DicomMetaData extends IIOMetadata {

    private final DicomObject fileMetaInformation;
    private final DicomObject dcm;
    private final ImageDescriptor desc;
    private final String transferSyntaxUID;

    public DicomMetaData(DicomInputStream dcmStream) throws IOException {
        this.fileMetaInformation = Objects.requireNonNull(dcmStream).readFileMetaInformation();
        this.dcm = dcmStream.readDataSet();
        this.desc = new ImageDescriptor(dcm);
        this.transferSyntaxUID =
            fileMetaInformation.getString(Tag.TransferSyntaxUID).orElse(dcmStream.getEncoding().transferSyntaxUID);
    }

    public DicomMetaData(DicomObject dcm, String transferSyntaxUID) {
        this.fileMetaInformation = null;
        this.dcm = Objects.requireNonNull(dcm);
        this.desc = new ImageDescriptor(dcm);
        this.transferSyntaxUID = Objects.requireNonNull(transferSyntaxUID);
    }

    public final DicomObject getFileMetaInformation() {
        return fileMetaInformation;
    }

    public final DicomObject getDicomObject() {
        return dcm;
    }

    public final ImageDescriptor getImageDescriptor() {
        return desc;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Node getAsTree(String formatName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void mergeTree(String formatName, Node root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    public String getTransferSyntaxUID() {
        return transferSyntaxUID;
    }
}
