package org.dcm4che6.img.util;
/**
 * @author Nicolas Roduit
 *
 */
public class MathUtil {
    private static final double DOUBLE_EPSILON = 1e-6;

    private MathUtil() {
    }

    public static boolean isEqual(double a, double b) {
        // Math.copySign is similar to Math.abs(x), but with different NaN semantics
        return Math.copySign(a - b, 1.0) <= DOUBLE_EPSILON || (a == b) // infinities equal themselves
            || (Double.isNaN(a) && Double.isNaN(b));
    }

    public static boolean isEqualToZero(double val) {
        return Math.copySign(val, 1.0) < DOUBLE_EPSILON;
    }
}
