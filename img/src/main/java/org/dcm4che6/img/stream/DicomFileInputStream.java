package org.dcm4che6.img.stream;

import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.imageio.ImageReadParam;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.img.DicomImageReadParam;
import org.dcm4che6.img.DicomMetaData;
import org.dcm4che6.img.data.Overlays;
import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.lut.ModalityLutModule;
import org.dcm4che6.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

/**
 * @author Nicolas Roduit
 *
 */
public class DicomFileInputStream extends DicomInputStream implements ImageReaderDescriptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomFileInputStream.class);

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

//    private int[] getActiveOverlayGroupOffsets(ImageReadParam param) {
//        if (param instanceof DicomImageReadParam) {
//            DicomImageReadParam dParam = (DicomImageReadParam) param;
//            Optional<PrDicomObject> psAttrs = dParam.getPresentationState();
//            if (psAttrs.isPresent())
//                return Overlays.getActiveOverlayGroupOffsets(psAttrs.get().getDicomObject());
//            else
//                return Overlays.getActiveOverlayGroupOffsets(metadata.getDicomObject(),
//                    dParam.getOverlayActivationMask());
//        }
//        return Overlays.getActiveOverlayGroupOffsets(metadata.getDicomObject(), 0xffff);
//    }
}
