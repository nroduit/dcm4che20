package org.dcm4che6.img;

public class DicomTranscodeParam {
    private final DicomImageReadParam readParam;
    private final DicomJpegWriteParam writeJpegParam;
    private final String outputTsuid;
    private boolean outputFmi;   
    
    public DicomTranscodeParam(String dstTsuid) {
        this(null, dstTsuid);
    }
    public DicomTranscodeParam(DicomImageReadParam readParam, String dstTsuid) {
        this.readParam = readParam == null ? new DicomImageReadParam() : readParam;
        this.outputTsuid = dstTsuid;
        if (DicomOutputData.isNativeSyntax(dstTsuid)) {
            this.writeJpegParam = null;
        } else {
            this.writeJpegParam = DicomJpegWriteParam.buildDicomImageWriteParam(dstTsuid);
        }
    }

    public DicomImageReadParam getReadParam() {
        return readParam;
    }

    public DicomJpegWriteParam getWriteJpegParam() {
        return writeJpegParam;
    }

    public boolean isOutputFmi() {
        return outputFmi;
    }

    public void setOutputFmi(boolean outputFmi) {
        this.outputFmi = outputFmi;
    }

    public String getOutputTsuid() {
        return outputTsuid;
    }

}
