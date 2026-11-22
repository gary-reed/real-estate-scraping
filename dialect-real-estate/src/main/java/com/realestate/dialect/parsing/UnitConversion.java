package com.realestate.dialect.parsing;

/**
 * Unit conversion for property surface areas (real-estate dialect).
 * Converts imperial / non-metric units to metric (m²).
 */
public final class UnitConversion {

    private UnitConversion() {} // utility class

    public static Double sqFtToMetersSq(Integer sqFt) {
        return sqFt != null ? sqFtToMetersSq(sqFt.doubleValue()) : null;
    }

    public static Double sqFtToMetersSq(Double sqFt) {
        return sqFt != null ? sqFt / 10.764 : null;
    }

    public static Double acresToMetersSq(Double acres) {
        return acres != null ? acres * 4046.86 : null;
    }

    public static Double hectaresToMetersSq(Double hectares) {
        return hectares != null ? hectares * 10000 : null;
    }

    public static Integer extractNumber(String text) {
        if (text == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
