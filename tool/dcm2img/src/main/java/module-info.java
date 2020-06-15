/**
 * @author Nicolas Roduit
 * @since Dec 2020
 */
module org.dcm4che.tool.dcm2img {
    requires org.dcm4che.base;
    requires org.dcm4che.codec;
    requires org.dcm4che.img;
    requires info.picocli;
    requires java.desktop;
    requires org.slf4j;

    opens org.dcm4che6.tool.dcm2img to info.picocli;
}