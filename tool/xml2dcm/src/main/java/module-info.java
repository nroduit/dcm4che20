/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2018
 */
module org.dcm4che.tool.xml2dcm {
    requires org.dcm4che.xml;
    requires info.picocli;
    opens org.dcm4che.tool.xml2dcm to info.picocli;
}