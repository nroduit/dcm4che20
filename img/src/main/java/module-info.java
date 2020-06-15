/**
 * @author Nicolas Roduit
 * @since Dec 2020
 */
module org.dcm4che.img {
    requires java.desktop;
    requires org.dcm4che.base;
    requires org.dcm4che.codec;
    requires org.slf4j;
    requires weasis.core.img;

    provides javax.imageio.spi.ImageReaderSpi with org.dcm4che6.img.DicomImageReaderSpi;

    exports org.dcm4che6.img;
    exports org.dcm4che6.img.data;
}
