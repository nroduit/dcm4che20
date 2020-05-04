package org.dcm4che6.img.stream;

import org.weasis.core.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Objects;

/**
 * @author Nicolas Roduit
 *
 */
public class SegmentInputStream extends InputStream {

    private final RandomAccessFile file;
    private final long offset;
    private long pos;
    private final long length;
    private long mark;

    public SegmentInputStream(RandomAccessFile randomAccessFile, long offset, long length) {
        this.file = Objects.requireNonNull(randomAccessFile);
        this.offset = offset;
        this.length = length;
        this.pos = offset;
        this.mark = offset;
    }

    @Override
    public int read() throws IOException {
        if (pos >= offset + length) {
            return -1;
        }
        file.seek(pos);
        int b = file.read();
        pos += 1;
        return b;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void close() throws IOException {
        FileUtil.safeClose(file);
        super.close();
    }

    public synchronized void mark() {
        mark = pos;
    }

    @Override
    public synchronized void mark(int readlimit) {
        mark = readlimit;
    }

    @Override
    public synchronized void reset() {
        pos = mark;
    }

    @Override
    public int read(byte[] b, int offset, int length) throws IOException {
        checkPosition();
        int total = file.read(b, offset, length);
        pos += total;
        return total;
    }

    @Override
    public int available() {
        int left = (int) (offset + length - pos);
        return left > 0 ? left : 0;
    }

    private void checkPosition() throws IOException {
        if (pos >= offset + length) {
            throw new IOException("no available byte in current stream");
        }
        file.seek(pos);
    }

    @Override
    public long skip(long n) {
        if (n <= 0) {
            return 0;
        }
        if (pos + n >= offset + length) {
            long skipped = offset + length - pos;
            pos = offset + length;
            return skipped;
        } else {
            pos += n;
            return n;
        }
    }

}