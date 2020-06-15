package org.dcm4che6.tool.dcm2img;

import org.dcm4che6.img.DicomImageReadParam;
import org.dcm4che6.img.ImageTranscodeParam;
import org.dcm4che6.img.Transcoder;
import org.dcm4che6.img.data.PrDicomObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Nicolas Roduit
 * @since Jun 2020
 */
@CommandLine.Command(
        name = "dcm2img",
        mixinStandardHelpOptions = true,
        versionProvider = Dcm2Img.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The dcm2img utility .",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = {"$ dcm2img -f TIF image.dcm outputPath",
                "Write a tif image into the outputPath by extracting the pixel data from image.dcm. Without the '-p' " +
                        "option, the default Window/Level is applied to obtain a 8 bit/pixel image."}
)
public class Dcm2Img implements Callable<Integer> {

    @CommandLine.Parameters(
            description = "Path of the DICOM input file.",
            index = "0")
    Path dcmfile;

    @CommandLine.Parameters(
            description = "Path of the output image. if the path is a directory then the filename is taken from the source path.",
            index = "1")
    Path outDir;

    @CommandLine.Parameters(
            description = "Path of the DICOM Presentation State file",
            arity = "0..1",
            index = "2")
    Path prfile;

    @CommandLine.Option(names = "-f", description = "Output image format: ${COMPLETION-CANDIDATES}")
    Transcoder.Format format = Transcoder.Format.JPEG;

    @CommandLine.Option(names = "-q", description = "JPEG compression quality between 1 to 100 (100 is the best lossy quality).")
    Integer jpegCompressionQuality = 80;

    @CommandLine.Option(names = "--overlay-color", description = "The color defined in Hex Code (RRGGBB or AARRGGBB with " +
            "alpha) for displaying overlays. Ex. red color is FF0000")
    Color overlayColor = new Color(36, 36, 148);

    @CommandLine.Option(names = "--no-pixel-padding", description = "Do not exclude the pixel padding values.")
    boolean noPixelPadding;

    @CommandLine.Option(names = "--inverse-lut", description = "Invert the lookup table")
    boolean inverseLut;

    @CommandLine.Option(names = "--win-level", arity = "2", description = "Set the Window/Level.")
    Double[] winLevel = null;

    @CommandLine.Option(names = "--win-level-index", description = "Select the embedded Window/Level where the index defined the position.")
    Integer winIndex = 0;

    @CommandLine.Option(names = "-p", description = "It preserves the raw data when the pixel depth is more than 8 bit. " +
            "The default value applies the W/L and is FALSE, the output image will be always a 8-bit per sample image.")
    boolean preservePixelDepth;

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new Dcm2Img());
        cl.registerConverter(Color.class, s -> hexadecimal2Color(s));
        cl.execute(args);
    }

    @Override
    public Integer call() throws Exception {
        DicomImageReadParam readParam = new DicomImageReadParam();
        readParam.setInverseLut(inverseLut);
        readParam.setApplyPixelPadding(!noPixelPadding);
        readParam.setOverlayColor(overlayColor);
        readParam.setWindowIndex(winIndex);
        readParam.setFillOutsideLutRange(true);
        if(winLevel != null){
            readParam.setWindowWidth(winLevel[0].doubleValue());
            readParam.setWindowCenter(winLevel[1].doubleValue());
        }
        if (prfile != null) {
            readParam.setPresentationState(PrDicomObject.getPresentationState(prfile.toString()));
        }
        ImageTranscodeParam params = new ImageTranscodeParam(readParam, format);
        params.setJpegCompressionQuality(jpegCompressionQuality);
        params.setPreserveRawImage(preservePixelDepth);
        Transcoder.dcm2image(dcmfile, outDir, params);

        System.out.println(String.format(
                "Extract the image(s) of %s to %s.", dcmfile, outDir));
        return 0;
    }


    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{Dcm2Img.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    public static Color hexadecimal2Color(String hexColor) {
        int intValue = 0xff000000;
        if (hexColor != null && hexColor.length() > 6) {
            intValue = (int) (Long.parseLong(hexColor, 16) & 0xffffffff);
        } else {
            intValue |= Integer.parseInt(hexColor, 16);
        }
        return new Color(intValue, true);
    }
}
