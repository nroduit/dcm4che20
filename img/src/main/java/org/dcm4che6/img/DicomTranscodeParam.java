package org.dcm4che6.img;

import org.dcm4che6.img.op.MaskArea;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DicomTranscodeParam {
    private final DicomImageReadParam readParam;
    private final DicomJpegWriteParam writeJpegParam;
    private final String outputTsuid;
    private final Map<String, MaskArea> maskMap;
    private boolean outputFmi;   
    
    public DicomTranscodeParam(String dstTsuid) {
        this(null, dstTsuid);
    }
    public DicomTranscodeParam(DicomImageReadParam readParam, String dstTsuid) {
        this.readParam = readParam == null ? new DicomImageReadParam() : readParam;
        this.outputTsuid = dstTsuid;
        this.maskMap = new HashMap<>();
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

    public void addMaskMap(Map<? extends String, ? extends MaskArea> maskMap){
        this.maskMap.putAll(maskMap);
    }

    public void addMask(String stationName, MaskArea maskArea){
        this.maskMap.put(stationName, maskArea);
    }

    public Map<String, MaskArea> getMaskMap() {
        return maskMap;
    }
}
