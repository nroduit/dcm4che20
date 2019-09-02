import java.nio.file.spi.FileTypeDetector;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2018
 */
module org.dcm4che.tool.jpg2dcm {
    requires org.dcm4che.base;
    requires org.dcm4che.codec;
    requires org.dcm4che.xml;
    requires info.picocli;
    requires java.xml;

    uses FileTypeDetector;

    opens org.dcm4che6.tool.jpg2dcm to info.picocli;
}