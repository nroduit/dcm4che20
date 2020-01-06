package org.dcm4che6.img.util;

/**
 * @author Nicolas Roduit
 *
 */
public class StringUtil {

    private StringUtil() {
    }

    public static boolean hasText(CharSequence str) {
        if (str == null || str.length() <= 0) {
            return false;
        }
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
