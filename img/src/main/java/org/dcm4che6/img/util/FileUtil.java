package org.dcm4che6.img.util;

import java.io.File;
import java.nio.file.Files;

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

    private static boolean deleteFile(File fileOrDirectory) {
        try {
            Files.delete(fileOrDirectory.toPath());
        } catch (Exception e) {
            LOGGER.error("Cannot delete", e);
            return false;
        }
        return true;
    }

    public static boolean delete(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return false;
        }

        if (fileOrDirectory.isDirectory()) {
            final File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    delete(child);
                }
            }
        }
        return deleteFile(fileOrDirectory);
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
    
    public static File getOutputFile(File src, File dst) {
        if (dst.isDirectory()) {
            return new File(dst.getPath(), src.getName());
        } else {
            return dst;
        }
    }

    public static File addFileSuffix(File file, String suffix) {
        if (!StringUtil.hasText(suffix)) {
            return file;
        }
        String path = file.getPath();
        int i = path.lastIndexOf('.');
        if (i > 0) {
            return new File(path.substring(0, i) + "-" + suffix + path.substring(i));
        }
        return new File(file.getPath() + suffix);
    }

}
