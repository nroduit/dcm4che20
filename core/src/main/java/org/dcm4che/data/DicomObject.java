package org.dcm4che.data;

import org.dcm4che.util.OptionalFloat;
import org.dcm4che.util.TagUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class DicomObject implements Iterable<DicomElement>, Externalizable {

    private final DicomInput dicomInput;
    private final long streamPosition;
    private final int itemLength;
    private volatile DicomSequence dcmSeq;
    private volatile ArrayList<DicomElement> elements;
    private volatile SpecificCharacterSet specificCharacterSet;
    private PrivateCreator lruPrivateCreator;
    int calculatedItemLength;

    public DicomObject() {
        this(null, -1L, -1, new ArrayList<>());
    }

    DicomObject(DicomInput dicomInput, long streamPosition, int itemLength, ArrayList<DicomElement> elements) {
        this.dicomInput = dicomInput;
        this.streamPosition = streamPosition;
        this.itemLength = itemLength;
        this.elements = elements;
    }

    public long getStreamPosition() {
        return streamPosition;
    }

    public int getItemLength() {
        return itemLength;
    }

    DicomObject containedBy(DicomSequence dcmSeq) {
        if (this.dcmSeq != null && dcmSeq != null)
            throw new IllegalStateException("Item already contained by " + dcmSeq);

        this.dcmSeq = dcmSeq;
        return this;
    }

    public Optional<DicomSequence> containedBy() {
        return Optional.ofNullable(dcmSeq);
    }

    public Optional<DicomObject> getParent() {
        return dcmSeq != null ? Optional.of(dcmSeq.containedBy()) : Optional.empty();
    }

    public boolean hasParent() {
        return dcmSeq != null;
    }

    public int nestingLevel() {
        return dcmSeq != null ? dcmSeq.containedBy().nestingLevel() + 1 : 0;
    }

    public boolean isEmpty() {
        return elements().isEmpty();
    }

    public int size() {
        return elements().size();
    }

    @Override
    public Iterator<DicomElement> iterator() {
        return elements().iterator();
    }

    public Stream<DicomElement> elementStream() {
        return elements().stream();
    }

    public void trimToSize() {
        ArrayList<DicomElement> elements = this.elements;
        if (elements != null) {
            elements.trimToSize();
            elements.forEach(DicomElement::trimToSize);
        }
    }

    public void purgeEncodedValues() {
        ArrayList<DicomElement> elements = this.elements;
        if (elements != null)
            elements.forEach(DicomElement::purgeEncodedValue);
    }

    public void purgeElements() {
        elements = null;
    }

    ArrayList<DicomElement> elements() {
        ArrayList<DicomElement> localRef = elements;
        if (localRef == null)
            synchronized (this) {
                if ((localRef = elements) != null)
                    return localRef;
                try {
                    elements = localRef = new ArrayList();
                    new DicomInputStream(dicomInput, streamPosition).parse(this, itemLength);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        return localRef;
    }

    public SpecificCharacterSet specificCharacterSet() {
        return specificCharacterSet != null
                ? specificCharacterSet
                : (specificCharacterSet = dcmSeq != null
                    ? dcmSeq.containedBy().specificCharacterSet()
                    : SpecificCharacterSet.getDefaultCharacterSet());
    }

    public DicomElement firstElement() {
        ArrayList<DicomElement> elements = elements();
        if (elements.isEmpty())
            throw new NoSuchElementException();

        return elements.get(0);
    }

    public DicomElement lastElement() {
        ArrayList<DicomElement> elements = elements();
        int size = elements.size();
        if (size == 0)
            throw new NoSuchElementException();

        return elements.get(size - 1);
    }

    public Optional<DicomElement> get(String privateCreator, int tag) {
        return privateCreator != null && TagUtils.isPrivateGroup(tag)
                ? get(creatorTag(privateCreator, tag, false), tag)
                : get(tag);
    }

    private Optional<DicomElement> get(OptionalInt creatorTag, int tag) {
        return creatorTag.isPresent()
                ? get(TagUtils.toPrivateTag(creatorTag.getAsInt(), tag))
                : Optional.empty();
    }

    private int creatorTag(String privateCreator, int tag) {
        return privateCreator != null && TagUtils.isPrivateGroup(tag)
                ? TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true).getAsInt(), tag)
                : tag;
    }

    private OptionalInt creatorTag(String value, int tag, boolean reserve) {
        int gggg0000 = tag & 0xffff0000;
        PrivateCreator privateCreator = lruPrivateCreator;
        if (privateCreator != null && privateCreator.match(value, gggg0000)
                && ((privateCreator.tag & 0xffff0000) == gggg0000)
                && lruPrivateCreator.value.equals(value)) {
            return OptionalInt.of(privateCreator.tag);
        }
        ArrayList<DicomElement> list = elements();
        int creatorTag = gggg0000 | 0x10;
        int i = binarySearch(list, creatorTag--);
        if (i < 0)
            i = -(i + 1);

        DicomElement el;
        while (i < list.size() && ((el = list.get(i)).tag() & 0xffffff00) == gggg0000) {
            creatorTag = el.tag();
            if (value.equals(el.stringValue(0).orElse(null))) {
                lruPrivateCreator = new PrivateCreator(el.tag(), el.stringValue(0));
                return OptionalInt.of(creatorTag);
            }
            i++;
        }
        if (!reserve)
            return OptionalInt.empty();

        list.add(i, new StringElement(this, ++creatorTag, VR.LO, value));
        lruPrivateCreator = new PrivateCreator(creatorTag, Optional.of(value));
        return OptionalInt.of(creatorTag);
    }

    public Optional<DicomElement> get(int tag) {
        ArrayList<DicomElement> list = elements();
        int i = binarySearch(list, tag);
        if (i >= 0) {
            return Optional.of(list.get(i));
        }
        return Optional.empty();
    }

    public Optional<String> getString(int tag) {
        return getString(tag, 0);
    }

    public Optional<String> getString(int tag, int index) {
        return get(tag).flatMap(el -> el.stringValue(index));
    }

    public Optional<String> getString(String privateCreator, int tag) {
        return getString(privateCreator, tag, 0);
    }

    public Optional<String> getString(String privateCreator, int tag, int index) {
        return get(privateCreator, tag).flatMap(el -> el.stringValue(index));
    }

    public Optional<String[]> getStrings(int tag) {
        return get(tag).map(DicomElement::stringValues);
    }

    public Optional<String[]> getStrings(String privateCreator, int tag) {
        return get(privateCreator, tag).map(DicomElement::stringValues);
    }

    public OptionalInt getInt(int tag) {
        return getInt(tag, 0);
    }

    public OptionalInt getInt(int tag, int index) {
        return get(tag).map(el -> el.intValue(index)).orElse(OptionalInt.empty());
    }

    public OptionalInt getInt(String privateCreator, int tag) {
        return getInt(privateCreator, tag, 0);
    }

    public OptionalInt getInt(String privateCreator, int tag, int index) {
        return get(privateCreator, tag).map(el -> el.intValue(index)).orElse(OptionalInt.empty());
    }

    public Optional<int[]> getInts(int tag) {
        return get(tag).map(DicomElement::intValues);
    }

    public Optional<int[]> getInts(String privateCreator, int tag) {
        return get(privateCreator, tag).map(DicomElement::intValues);
    }

    public OptionalFloat getFloat(int tag) {
        return getFloat(tag, 0);
    }

    public OptionalFloat getFloat(int tag, int index) {
        return get(tag).map(el -> el.floatValue(index)).orElse(OptionalFloat.empty());
    }

    public OptionalFloat getFloat(String privateCreator, int tag) {
        return getFloat(privateCreator, tag, 0);
    }

    public OptionalFloat getFloat(String privateCreator, int tag, int index) {
        return get(privateCreator, tag).map(el -> el.floatValue(index)).orElse(OptionalFloat.empty());
    }

    public Optional<float[]> getFloats(int tag) {
        return get(tag).map(DicomElement::floatValues);
    }

    public Optional<float[]> getFloats(String privateCreator, int tag) {
        return get(privateCreator, tag).map(DicomElement::floatValues);
    }

    public OptionalDouble getDouble(int tag) {
        return getDouble(tag, 0);
    }

    public OptionalDouble getDouble(int tag, int index) {
        return get(tag).map(el -> el.doubleValue(index)).orElse(OptionalDouble.empty());
    }

    public OptionalDouble getDouble(String privateCreator, int tag) {
        return getDouble(privateCreator, tag, 0);
    }

    public OptionalDouble getDouble(String privateCreator, int tag, int index) {
        return get(privateCreator, tag).map(el -> el.doubleValue(index)).orElse(OptionalDouble.empty());
    }

    public Optional<double[]> getDoubles(int tag) {
        return get(tag).map(DicomElement::doubleValues);
    }

    public Optional<double[]> getDoubles(String privateCreator, int tag) {
        return get(privateCreator, tag).map(DicomElement::doubleValues);
    }

    public DicomElement add(DicomElement el) {
        if (el.tag() == Tag.SpecificCharacterSet)
            specificCharacterSet = SpecificCharacterSet.valueOf(el.stringValues());

        List<DicomElement> list = elements();
        if (list.isEmpty() || Integer.compareUnsigned(list.get(list.size()-1).tag(), el.tag()) < 0) {
            list.add(el);
            return null;
        }
        int i = binarySearch(list, el.tag());
        if (i < 0) {
            list.add(-(i + 1), el);
            return null;
        }
        return list.set(i, el);
    }

    public DicomElement setNull(int tag, VR vr) {
        if (tag == Tag.SpecificCharacterSet)
            specificCharacterSet = SpecificCharacterSet.getDefaultCharacterSet();

        return add(vr.type.elementOf(this, tag, vr));
    }

    public DicomElement setNull(String privateCreator, int tag, VR vr) {
        return setNull(creatorTag(privateCreator, tag), vr);
    }

    public DicomElement setBytes(int tag, VR vr, byte[] val) {
        return add(vr.type.elementOf(this, tag, vr, val));
    }

    public DicomElement setBytes(String privateCreator, int tag, VR vr, byte[] val) {
        return setBytes(creatorTag(privateCreator, tag), vr, val);
    }

    public DicomElement setInt(int tag, VR vr, int... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setInt(String privateCreator, int tag, VR vr, int... vals) {
        return setInt(creatorTag(privateCreator, tag), vr, vals);
    }

    public DicomElement setFloat(int tag, VR vr, float... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setFloat(String privateCreator, int tag, VR vr, float... vals) {
        return setFloat(creatorTag(privateCreator, tag), vr, vals);
    }

    public DicomElement setDouble(int tag, VR vr, double... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setDouble(String privateCreator, int tag, VR vr, double... vals) {
        return setDouble(creatorTag(privateCreator, tag), vr, vals);
    }

    public DicomElement setString(int tag, VR vr, String val) {
        return add(vr.type.elementOf(this, tag, vr, val));
    }

    public DicomElement setString(int tag, VR vr, String... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setString(String privateCreator, int tag, VR vr, String val) {
        return setString(creatorTag(privateCreator, tag), vr, val);
    }

    public DicomElement setString(String privateCreator, int tag, VR vr, String... vals) {
        return setString(creatorTag(privateCreator, tag), vr, vals);
    }

    public DicomElement setBulkData(int tag, VR vr, String uri, String uuid) {
        return add(new BulkDataElement(this, tag, vr, uri, uuid));
    }

    public DicomElement setBulkData(String privateCreator, int tag, VR vr, String uri, String uuid) {
        return setBulkData(creatorTag(privateCreator, tag), vr, uri, uuid);
    }

    public DicomSequence newDicomSequence(int tag) {
        DicomSequence seq = new DicomSequence(this, tag);
        add(seq);
        return seq;
    }

    public DicomSequence newDicomSequence(String privateCreator, int tag) {
        return newDicomSequence(creatorTag(privateCreator, tag));
    }

    private static int binarySearch(List<DicomElement> l, int tag) {
        if (l == null)
            return -1;

        int low = 0;
        int high = l.size()-1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = Integer.compareUnsigned(l.get(mid).tag(), tag);
            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // tag found
        }
        return -(low + 1);  // tag not found
    }

    public Optional<String> getPrivateCreator(int tag) {
        return TagUtils.isPrivateTag(tag)
                ? privateCreator(TagUtils.creatorTagOf(tag)).value
                : Optional.empty();
    }

    private PrivateCreator privateCreator(int tag) {
        PrivateCreator privateCreator = lruPrivateCreator;
        if (privateCreator == null || privateCreator.tag != tag) {
            lruPrivateCreator = privateCreator = new PrivateCreator(tag, getString(tag));
        }
        return privateCreator;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        DicomOutputStream writer = new DicomOutputStream(new OutputStream() {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void write(int i) throws IOException {
                out.write(i);
            }
        }).withEncoding(DicomEncoding.SERIALIZE);
        writer.writeDataSet(this);
        writer.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        new DicomInputStream(new InputStream() {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return in.read(b, off, len);
            }

            @Override
            public int read() throws IOException {
                return in.read();
            }
        }).withEncoding(DicomEncoding.SERIALIZE).readDataSet(this);
    }

    public StringBuilder appendNestingLevel(StringBuilder sb) {
        if (dcmSeq != null)
            dcmSeq.containedBy().appendNestingLevel(sb).append('>');
        return sb;
    }

    public DicomObject createFileMetaInformation(String tsuid) {
        return createFileMetaInformation(
                getString(Tag.SOPInstanceUID).orElse(null),
                getString(Tag.SOPClassUID).orElse(null),
                tsuid);
    }

    public static DicomObject createFileMetaInformation(String iuid, String cuid, String tsuid) {
        if (iuid == null || iuid.isEmpty())
            throw new IllegalArgumentException("Missing SOP Instance UID");
        if (cuid == null || cuid.isEmpty())
            throw new IllegalArgumentException("Missing SOP Class UID");
        if (tsuid == null || tsuid.isEmpty())
            throw new IllegalArgumentException("Missing Transfer Syntax UID");

        DicomObject fmi = new DicomObject();
        fmi.setBytes(Tag.FileMetaInformationVersion, VR.OB, new byte[]{0, 1});
        fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, cuid);
        fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, iuid);
        fmi.setString(Tag.TransferSyntaxUID, VR.UI, tsuid);
        fmi.setString(Tag.ImplementationClassUID, VR.UI, Implementation.CLASS_UID);
        fmi.setString(Tag.ImplementationVersionName, VR.SH, Implementation.VERSION_NAME);
        return fmi;
    }

    private static class PrivateCreator {
        final int tag;
        final Optional<String> value;

        PrivateCreator(int tag, Optional<String> value) {
            this.tag = tag;
            this.value = value;
        }

        boolean match(String otherValue, int gggg0000) {
            return value.filter(otherValue::equals).isPresent()
                    && (tag & 0xffff0000) == gggg0000;
        }
    }

}
