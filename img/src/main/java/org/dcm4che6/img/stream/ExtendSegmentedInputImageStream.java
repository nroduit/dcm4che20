package org.dcm4che6.img.stream;

import java.nio.file.Path;


/**
 * @author Nicolas Roduit
 * @since Mar 2018
 */
public class ExtendSegmentedInputImageStream {
    private final Path path;
    private final long[] segmentPositions;
    private final int[] segmentLengths;
    private final ImageDescriptor imageDescriptor;

    public ExtendSegmentedInputImageStream(Path path, long[] segmentPositions, int[] segmentLengths, ImageDescriptor imageDescriptor) {
        this.path = path;
        this.segmentPositions = segmentPositions;
        this.segmentLengths = segmentLengths;
        this.imageDescriptor = imageDescriptor;
    }

    public long[] getSegmentPositions() {
        return segmentPositions;
    }

    public int[] getSegmentLengths() {
        return segmentLengths;
    }

    public Path getPath() {
        return path;
    }

    public ImageDescriptor getImageDescriptor() {
        return imageDescriptor;
    }
}
