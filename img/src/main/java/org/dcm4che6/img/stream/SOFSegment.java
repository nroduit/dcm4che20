package org.dcm4che6.img.stream;

import java.io.EOFException;
import java.io.IOException;

/**
 * @author Nicolas Roduit
 * @since Mar 2018
 */
public class SOFSegment {
    private final boolean jfif;
    private final int marker;
    private final int samplePrecision;
    private final int lines; // height
    private final int samplesPerLine; // width
    private final int components;

    SOFSegment(boolean jfif, int marker, int samplePrecision, int lines, int samplesPerLine, int components) {
        this.jfif = jfif;
        this.marker = marker;
        this.samplePrecision = samplePrecision;
        this.lines = lines;
        this.samplesPerLine = samplesPerLine;
        this.components = components;
    }

    public boolean isJFIF() {
        return jfif;
    }

    public int getMarker() {
        return marker;
    }

    public int getSamplePrecision() {
        return samplePrecision;
    }

    public int getLines() {
        return lines;
    }

    public int getSamplesPerLine() {
        return samplesPerLine;
    }

    public int getComponents() {
        return components;
    }

    @Override
    public String toString() {
        return String.format("SOF%d[%04x, precision: %d, lines: %d, samples/line: %d]", marker & 0xff - 0xc0, marker,
            samplePrecision, lines, samplesPerLine);
    }
    

    public static SOFSegment getSOFSegment(SegmentInputStream iis) throws IOException {
        iis.mark();
        try {
            boolean jfif = false;
            int byte1 = iis.read();
            int byte2 = iis.read();
            // Magic numbers for JPEG (general jpeg marker)
            if ((byte1 != 0xFF) || (byte2 != 0xD8)) {
                return null;
            }
            do {
                byte1 = iis.read();
                byte2 = iis.read();
                // Something wrong, but try to read it anyway
                if (byte1 != 0xFF) {
                    break;
                }
                // Start of scan
                if (byte2 == 0xDA) {
                    break;
                }
                // Start of Frame, also known as SOF55, indicates a JPEG-LS file.
                if (byte2 == 0xF7) {
                    return getSOF(iis, jfif, (byte1 << 8) + byte2);
                }
                // 0xffc0: // SOF_0: JPEG baseline
                // 0xffc1: // SOF_1: JPEG extended sequential DCT
                // 0xffc2: // SOF_2: JPEG progressive DCT
                // 0xffc3: // SOF_3: JPEG lossless sequential
                if ((byte2 >= 0xC0) && (byte2 <= 0xC3)) {
                    return getSOF(iis, jfif, (byte1 << 8) + byte2);
                }
                // 0xffc5: // SOF_5: differential (hierarchical) extended sequential, Huffman
                // 0xffc6: // SOF_6: differential (hierarchical) progressive, Huffman
                // 0xffc7: // SOF_7: differential (hierarchical) lossless, Huffman
                if ((byte2 >= 0xC5) && (byte2 <= 0xC7)) {
                    return getSOF(iis, jfif, (byte1 << 8) + byte2);
                }
                // 0xffc9: // SOF_9: extended sequential, arithmetic
                // 0xffca: // SOF_10: progressive, arithmetic
                // 0xffcb: // SOF_11: lossless, arithmetic
                if ((byte2 >= 0xC9) && (byte2 <= 0xCB)) {
                    return getSOF(iis, jfif, (byte1 << 8) + byte2);
                }
                // 0xffcd: // SOF_13: differential (hierarchical) extended sequential, arithmetic
                // 0xffce: // SOF_14: differential (hierarchical) progressive, arithmetic
                // 0xffcf: // SOF_15: differential (hierarchical) lossless, arithmetic
                if ((byte2 >= 0xCD) && (byte2 <= 0xCF)) {
                    return getSOF(iis, jfif, (byte1 << 8) + byte2);
                }
                if (byte2 == 0xE0) {
                    jfif = true;
                }
                int length = iis.read() << 8;
                length += iis.read();
                length -= 2;
                while (length > 0) {
                    length -= iis.skip(length);
                }
            } while (true);
            return null;
        } finally {
            iis.reset();
        }
    }

    protected static SOFSegment getSOF(SegmentInputStream iis, boolean jfif, int marker) throws IOException {
        readUnsignedShort(iis);
        int samplePrecision = readUnsignedByte(iis);
        int lines = readUnsignedShort(iis);
        int samplesPerLine = readUnsignedShort(iis);
        int componentsInFrame = readUnsignedByte(iis);
        return new SOFSegment(jfif, marker, samplePrecision, lines, samplesPerLine, componentsInFrame);
    }
    
    private static int readUnsignedByte(SegmentInputStream iis) throws IOException {
        int ch = iis.read();
        if (ch < 0) {
            throw new EOFException();
        }
        return ch;
    }

    private static int readUnsignedShort(SegmentInputStream iis) throws IOException {
        int ch1 = iis.read();
        int ch2 = iis.read();
        if ((ch1 | ch2) < 0) {
            throw new EOFException();
        }
        return (ch1 << 8) + ch2;
    }
}