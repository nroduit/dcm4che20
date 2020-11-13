package org.dcm4che6.tool.dcm2dcm;

import org.dcm4che6.data.UID;
import org.dcm4che6.img.DicomImageReadParam;
import org.dcm4che6.img.DicomTranscodeParam;
import org.dcm4che6.img.Transcoder;
import org.dcm4che6.img.data.TransferSyntaxType;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * @author Nicolas Roduit
 * @since Nov 2020
 */
@CommandLine.Command(
        name = "dcm2dcm",
        mixinStandardHelpOptions = true,
        versionProvider = Dcm2Dcm.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The dcm2img utility allows converting DICOM files with a specific transfer syntax.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = {"$ dcm2dcm -t JPEG_2000 -o \"/tmp/dcm/\" \"home/user/image.dcm\" \"home/user/dicom/\"",
                "Transcode DICOM file and folder into jpeg2000 syntax copy them into \"/tmp/dcm/\"." }
)
public class Dcm2Dcm implements Callable<Integer> {
    public enum TransferSyntax {
        RAW_EXPLICIT_LE ("Explicit VR Little Endian", UID.ExplicitVRLittleEndian),
        JPEG_BASELINE ("JPEG Baseline (Process 1)", UID.JPEGBaseline1),
        JPEG_EXTENDED ("JPEG Extended (Process 2 & 4),)", UID.JPEGExtended24),
        JPEG_SPECTRAL ("JPEG Spectral Selection, Non-Hierarchical (Process 6 & 8) (Retired)", UID.JPEGSpectralSelectionNonHierarchical68Retired),
        JPEG_PROGRESSIVE ("JPEG Full Progression, Non-Hierarchical (Process 10 & 12) (Retired)", UID.JPEGFullProgressionNonHierarchical1012Retired),
        JPEG_LOSSLESS_14 ("JPEG Lossless, Non-Hierarchical (Process 14)", UID.JPEGLosslessNonHierarchical14),
        JPEG_LOSSLESS ("JPEG Lossless, Non-Hierarchical, First-Order Prediction (Process 14 [Selection Value 1])", UID.JPEGLossless),
        JPEG_LS ("JPEG-LS Lossless Image Compression", UID.JPEGLSLossless),
        JPEG_LS_LOSSY ("JPEG-LS Lossy (Near-Lossless) Image Compression", UID.JPEGLSLossyNearLossless),
        JPEG_2000 ("JPEG 2000 Image Compression (Lossless Only)", UID.JPEG2000LosslessOnly),
        JPEG_2000_LOSSY ("JPEG 2000 Image Compression,", UID.JPEG2000);

        private final String name;
        private final String uid;

        TransferSyntax(String name, String uid){
            this.name = name;
            this.uid = uid;
        }
    }

    @CommandLine.Parameters(
            description = "List of paths which can be DICOM file or folder.",
            arity = "1..*",
            index = "0")
    Path[] paths;

    @CommandLine.Option(
            names = {"-o", "--output"},
            required = true,
            description = "Path of the output image. if the path is a directory then the filename is taken from the source path.")
    Path outDir;

    @CommandLine.Option(names = "-t", description = "Transfer syntax: ${COMPLETION-CANDIDATES}")
    TransferSyntax syntax = TransferSyntax.RAW_EXPLICIT_LE;

    @CommandLine.Option(names = "-q", description = "Lossy JPEG compression quality between 1 to 100 (100 is the best lossy quality).")
    Integer jpegCompressionQuality = 80;

    @CommandLine.Option(names = "--rgb-lossy", negatable = true, description = "Keep RGB model with JPEG lossy." +
            "If FALSE the reader force using YBR color model")
    boolean keepRgb = false;

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new Dcm2Dcm());
        cl.execute(args);
    }

    @Override
    public Integer call() throws Exception {
        DicomImageReadParam readParam = new DicomImageReadParam();
        readParam.setKeepRgbForLossyJpeg(keepRgb);
        DicomTranscodeParam params = new DicomTranscodeParam(readParam, syntax.uid);
        if (params.getWriteJpegParam() != null && TransferSyntaxType.isLossyCompression(syntax.uid)) {
            params.getWriteJpegParam().setCompressionQuality(jpegCompressionQuality);
        }

        System.out.println(String.format("Converting all the images to %s.", syntax.name));
        for (Path p: paths) {
            if(Files.isDirectory(p)) {
                try (Stream<Path> walk = Files.walk(p)) {
                    walk.forEach( path -> {
                        try {
                            Path out = Path.of(outDir.toString(), p.relativize(path).toString());
                            if(Files.isRegularFile(path)) {
                                Transcoder.dcm2dcm(path, out, params);
                                System.out.println(String.format("Transcode \"%s\" in \"%s\".", path, out));
                            }
                            else {
                                Files.createDirectories(out);
                            }
                        } catch (Exception e) {
                            System.out.println(String.format("Cannot convert \"%s\".", path));
                        }
                    });
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
            else{
                Transcoder.dcm2dcm(p, outDir, params);
                System.out.println(String.format("Transcode \"%s\" in \"%s\".", p, outDir));
            }
        }
        return 0;
    }

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{Dcm2Dcm.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }
}
