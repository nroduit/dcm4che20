package org.dcm4che6.img.lut;

import org.dcm4che6.img.DicomImageAdapter;
import org.dcm4che6.img.data.PrDicomObject;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.LutShape.eFunction;
import org.weasis.opencv.op.lut.PresentationStateLut;
import org.weasis.opencv.op.lut.WlPresentation;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.awt.image.DataBuffer;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.*;

/**
 * @author Nicolas Roduit
 */
public class PresetWindowLevel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetWindowLevel.class);

    private static final Map<String, List<PresetWindowLevel>> presetListByModality = getPresetListByModality();

    private final String name;
    private final Double window;
    private final Double level;
    private final LutShape shape;

    public PresetWindowLevel(String name, Double window, Double level, LutShape shape) {
        this.name = Objects.requireNonNull(name);
        this.window = Objects.requireNonNull(window);
        this.level = Objects.requireNonNull(level);
        this.shape = Objects.requireNonNull(shape);
    }

    public String getName() {
        return name;
    }

    public Double getWindow() {
        return window;
    }

    public Double getLevel() {
        return level;
    }

    public LutShape getLutShape() {
        return shape;
    }

    public double getMinBox() {
        return level - window / 2.0;

    }

    public double getMaxBox() {
        return level + window / 2.0;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PresetWindowLevel that = (PresetWindowLevel) o;
        return name.equals(that.name) && window.equals(that.window) && level.equals(that.level)
                && shape.equals(that.shape);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, window, level, shape);
    }

    public static List<PresetWindowLevel> getPresetCollection(DicomImageAdapter adapter, String type,
                                                              WlPresentation wl) {
        if (adapter == null) {
            return null;
        }

        String dicomKeyWord = " " + type;

        ArrayList<PresetWindowLevel> presetList = new ArrayList<>();
        ImageDescriptor desc = adapter.getImageDescriptor();
        VoiLutModule vLut = desc.getVoiLUT();
        List<Double> levelList = vLut.getWindowCenter();
        List<Double> windowList = vLut.getWindowWidth();

        // optional attributes
        List<String> wlExplanationList = vLut.getWindowCenterWidthExplanation();
        Optional<String> lutFunctionDescriptor = vLut.getVoiLutFunction();

        LutShape defaultLutShape = LutShape.LINEAR; // Implicitly defined as default function in DICOM standard
        if (lutFunctionDescriptor.isPresent()) {
            if ("SIGMOID".equalsIgnoreCase(lutFunctionDescriptor.get())) {
                defaultLutShape = new LutShape(eFunction.SIGMOID, eFunction.SIGMOID + dicomKeyWord);
            } else if ("LINEAR".equalsIgnoreCase(lutFunctionDescriptor.get())) {
                defaultLutShape = new LutShape(eFunction.LINEAR, eFunction.LINEAR + dicomKeyWord);
            }
        }

        if (!levelList.isEmpty() && !windowList.isEmpty()) {
            String defaultExplanation = "Default";

            int k = 1;

            for (int i = 0; i < levelList.size(); i++) {
                String explanation = defaultExplanation + " " + k;
                if (i < wlExplanationList.size()) {
                    String wexpl = wlExplanationList.get(i);
                    if (StringUtil.hasText(wexpl)) {
                        explanation = wexpl;
                    }
                }

                PresetWindowLevel preset = new PresetWindowLevel(explanation + dicomKeyWord, windowList.get(i),
                        levelList.get(i), defaultLutShape);
                if (!presetList.contains(preset)) {
                    presetList.add(preset);
                    k++;
                }
            }
        }

        List<LookupTableCV> voiLUTsData = getVoiLutData(desc, wl);
        List<String> voiLUTsExplanation = getVoiLUTExplanation(desc, wl);

        if (!voiLUTsData.isEmpty()) {
            String defaultExplanation = "VOI LUT";

            for (int i = 0; i < voiLUTsData.size(); i++) {
                String explanation = defaultExplanation + " " + i;

                if (voiLUTsExplanation != null && i < voiLUTsExplanation.size()) {
                    String exp = voiLUTsExplanation.get(i);
                    if (StringUtil.hasText(exp)) {
                        explanation = exp;
                    }
                }

                PresetWindowLevel preset =
                        buildPresetFromLutData(adapter, voiLUTsData.get(i), wl, explanation + dicomKeyWord);
                if (preset == null) {
                    continue;
                }
                presetList.add(preset);
            }
        }

        PresetWindowLevel autoLevel = new PresetWindowLevel("Auto Level [Image]", adapter.getFullDynamicWidth(wl),
                adapter.getFullDynamicCenter(wl), defaultLutShape);
        presetList.add(autoLevel);

        // Exclude Secondary Capture CT
        if (adapter.getBitsStored() > 8) {
            List<PresetWindowLevel> modPresets = presetListByModality.get(desc.getModality());
            if (modPresets != null) {
                presetList.addAll(modPresets);
            }
        }

        return presetList;
    }

    private static List<LookupTableCV> getVoiLutData(ImageDescriptor desc, WlPresentation wl) {
        List<LookupTableCV> luts = new ArrayList<>();
        PresentationStateLut pr = wl.getPresentationState();
        if (pr instanceof PrDicomObject) {
            Optional<VoiLutModule> vlut = ((PrDicomObject) pr).getVoiLUT();
            if (vlut.isPresent()) {
                luts.addAll(vlut.get().getLut());
            }
        }
        if (!desc.getVoiLUT().getLut().isEmpty()) {
            luts.addAll(desc.getVoiLUT().getLut());
        }
        return luts;
    }

    private static List<String> getVoiLUTExplanation(ImageDescriptor desc, WlPresentation wl) {
        List<String> luts = new ArrayList<>();
        PresentationStateLut pr = wl.getPresentationState();
        if (pr instanceof PrDicomObject) {
            Optional<VoiLutModule> vlut = ((PrDicomObject) pr).getVoiLUT();
            if (vlut.isPresent()) {
                luts.addAll(vlut.get().getLutExplanation());
            }
        }
        if (!desc.getVoiLUT().getLut().isEmpty()) {
            luts.addAll(desc.getVoiLUT().getLutExplanation());
        }
        return luts;
    }

    public static PresetWindowLevel buildPresetFromLutData(DicomImageAdapter adapter, LookupTableCV voiLUTsData,
                                                           WlPresentation wl, String explanation) {
        if (adapter == null || voiLUTsData == null || explanation == null) {
            return null;
        }

        Object inLut;

        if (voiLUTsData.getDataType() == DataBuffer.TYPE_BYTE) {
            inLut = voiLUTsData.getByteData(0);
        } else if (voiLUTsData.getDataType() <= DataBuffer.TYPE_SHORT) {
            inLut = voiLUTsData.getShortData(0);
        } else {
            return null;
        }

        int minValueLookup = voiLUTsData.getOffset();
        int maxValueLookup = voiLUTsData.getOffset() + Array.getLength(inLut) - 1;

        minValueLookup = Math.min(minValueLookup, maxValueLookup);
        maxValueLookup = Math.max(minValueLookup, maxValueLookup);
        int minAllocatedValue = adapter.getMinAllocatedValue(wl);
        if (minValueLookup < minAllocatedValue) {
            minValueLookup = minAllocatedValue;
        }
        int maxAllocatedValue = adapter.getMaxAllocatedValue(wl);
        if (maxValueLookup > maxAllocatedValue) {
            maxValueLookup = maxAllocatedValue;
        }

        double fullDynamicWidth = (double) maxValueLookup - minValueLookup;
        double fullDynamicCenter = minValueLookup + fullDynamicWidth / 2f;

        LutShape newLutShape = new LutShape(voiLUTsData, explanation);
        return new PresetWindowLevel(newLutShape.toString(), fullDynamicWidth, fullDynamicCenter, newLutShape);
    }

    public static Map<String, List<PresetWindowLevel>> getPresetListByModality() {

        Map<String, List<PresetWindowLevel>> presets = new TreeMap<>();

        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            // TODO convert in Path. Allow to override with a System property
            File file = new File(PresetWindowLevel.class.getResource("presets.xml").getFile());
            if (!file.canRead()) {
                return Collections.emptyMap();
            }
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // disable external entities for security
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            stream = new FileInputStream(file); // $NON-NLS-1$
            xmler = factory.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                if (eventType == XMLStreamConstants.START_ELEMENT && "presets".equals(xmler.getName().getLocalPart())) {
                    while (xmler.hasNext()) {
                        readPresetListByModality(xmler, presets);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Cannot read presets file! ", e);
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return presets;
    }

    private static void readPresetListByModality(XMLStreamReader xmler, Map<String, List<PresetWindowLevel>> presets)
            throws XMLStreamException {
        int eventType = xmler.next();
        String key;
        if (eventType == XMLStreamConstants.START_ELEMENT) {
            key = xmler.getName().getLocalPart();
            if ("preset".equals(key) && xmler.getAttributeCount() >= 4) {
                String name = xmler.getAttributeValue(null, "name");
                try {
                    String modality = xmler.getAttributeValue(null, "modality");
                    double window = Double.parseDouble(xmler.getAttributeValue(null, "window"));
                    double level = Double.parseDouble(xmler.getAttributeValue(null, "level"));
                    String shape = xmler.getAttributeValue(null, "shape");
                    LutShape lutShape = LutShape.getLutShape(shape);
                    PresetWindowLevel preset =
                            new PresetWindowLevel(name, window, level, lutShape == null ? LutShape.LINEAR : lutShape);
                    List<PresetWindowLevel> presetList = presets.computeIfAbsent(modality, k -> new ArrayList<>());
                    presetList.add(preset);
                } catch (Exception e) {
                    LOGGER.error("Preset {} cannot be read from xml file", name, e);
                }
            }
        }
    }
}