package org.dcm4che6.img.stream;

import java.nio.file.Path;


/**
 * @author Nicolas Roduit
 * @since Mar 2018
 */
public class ExtendSegmentedInputImageStream {
    private final Path path;
    private final long[] segmentPositions;
    private final long[] segmentLengths;
    private final ImageDescriptor imageDescriptor;

    public ExtendSegmentedInputImageStream(Path path, long[] segmentPositions, int[] segmentLengths, ImageDescriptor imageDescriptor) {
        this.path = path;
        this.segmentPositions = segmentPositions;
        this.segmentLengths = segmentLengths == null ? null : getDoubleArray(segmentLengths);
        this.imageDescriptor = imageDescriptor;
    }

    public long[] getSegmentPositions() {
        return segmentPositions;
    }

    public long[] getSegmentLengths() {
        return segmentLengths;
    }

    public Path getPath() {
        return path;
    }
    
    public static double[] getDoubleArray(long[] array) {
        double[] a = new double[array.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = array[i];
        }
        return a;
    }
    
    public static long[] getDoubleArray(int[] array) {
        long[] a = new long[array.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = array[i];
        }
        return a;
    }

    public ImageDescriptor getImageDescriptor() {
        return imageDescriptor;
    }
}
