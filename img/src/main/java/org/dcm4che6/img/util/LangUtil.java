package org.dcm4che6.img.util;

import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
/**
 * @author Nicolas Roduit
 *
 */
public class LangUtil {
    
    private LangUtil() {
    }

    public static <T> Iterable<T> emptyIfNull(Iterable<T> iterable) {
        return iterable == null ? Collections.<T> emptyList() : iterable;
    }

    public static boolean getNULLtoFalse(Boolean val) {
        if (val != null) {
            return val;
        }
        return false;
    }

    public static boolean getNULLtoTrue(Boolean val) {
        if (val != null) {
            return val;
        }
        return true;
    }

    public static OptionalDouble getOptionalDouble(Double val) {
        return val == null ? OptionalDouble.empty() : OptionalDouble.of(val);
    }
    
    public static OptionalInt getOptionalInteger(Integer val) {
        return val == null ? OptionalInt.empty() : OptionalInt.of(val);
    }
}
