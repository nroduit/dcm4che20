package org.dcm4che6.img.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nicolas Roduit
 *
 */
public final class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    public static final int FILE_BUFFER = 4096;

    private FileUtil() {
    }

    public static void safeClose(final AutoCloseable object) {
        if (object != null) {
            try {
                object.close();
            } catch (Exception e) {
                LOGGER.error("Cannot close AutoCloseable", e);
            }
        }
    }

    public static void safeClose(XMLStreamWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (XMLStreamException e) {
                LOGGER.error("Cannot close XMLStreamWriter", e);
            }
        }
    }

    public static void safeClose(XMLStreamReader xmler) {
        if (xmler != null) {
            try {
                xmler.close();
            } catch (XMLStreamException e) {
                LOGGER.error("Cannot close XMLStreamException", e);
            }
        }
    }

    public static boolean writeStream(InputStream inputStream, Path outFile, boolean closeInputStream) {
        try (OutputStream outputStream = Files.newOutputStream(outFile)) {
            byte[] buf = new byte[FILE_BUFFER];
            int offset;
            while ((offset = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, offset);
            }
            outputStream.flush();
            return true;
        } catch (IOException e) {
            FileUtil.delete(outFile);
            LOGGER.error("Writing file: {}", outFile, e); //$NON-NLS-1$
            return false;
        } finally {
            if (closeInputStream) {
                FileUtil.safeClose(inputStream);
            }
        }
    }

    private static boolean deleteFile(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.error("Cannot delete", e);
        }
        return false;
    }

    public static boolean delete(Path fileOrDirectory) {
        if (!Files.isDirectory(fileOrDirectory)) {
            return deleteFile(fileOrDirectory);
        }

        try (Stream<Path> walk = Files.walk(fileOrDirectory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(FileUtil::deleteFile);
            return true;
        } catch (IOException e) {
            LOGGER.error("Cannot delete", e);
        }
        return false;
    }

    public static String nameWithoutExtension(String fn) {
        if (fn == null) {
            return null;
        }
        int i = fn.lastIndexOf('.');
        if (i > 0) {
            return fn.substring(0, i);
        }
        return fn;
    }

    public static String getExtension(String fn) {
        if (fn == null) {
            return "";
        }
        int i = fn.lastIndexOf('.');
        if (i > 0) {
            return fn.substring(i);
        }
        return "";
    }

    // From: https://programming.guide/worlds-most-copied-so-snippet.html
    public static String humanReadableByte(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        long absBytes = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absBytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(absBytes) / Math.log(unit));
        long th = (long) (Math.pow(unit, exp) * (unit - 0.05));
        if (exp < 6 && absBytes >= th - ((th & 0xfff) == 0xd00 ? 52 : 0))
            exp++;
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        if (exp > 4) {
            bytes /= unit;
            exp -= 1;
        }
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static Path getOutputPath(Path src, Path dst) {
        if (Files.isDirectory(dst)) {
            return Path.of(dst.toString(), src.getFileName().toString());
        } else {
            return dst;
        }
    }

    public static Path addFileIndex(Path path, int index, int indexSize) {
        if (indexSize < 1) {
            return path;
        }
        String pattern = "$1-%0" + indexSize + "d$2";
        String insert = String.format(pattern, index);
        return path.resolveSibling(path.getFileName().toString().replaceFirst("(.*?)(\\.[^.]+)?$", insert));
    }
}
