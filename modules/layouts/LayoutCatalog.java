package frameluma.layouts;

import frameluma.core.LocalizedText;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class LayoutCatalog {
    public static final String DEFAULT = GeneratedLayouts.DEFAULT_ID;
    private static final LayoutProfile[] PROFILES = GeneratedLayouts.create();

    private LayoutCatalog() {}

    public static LayoutProfile[] all() {
        return PROFILES.clone();
    }

    public static LayoutProfile require(String id) {
        for (LayoutProfile profile : PROFILES) if (profile.id().equals(id)) return profile;
        throw new IllegalArgumentException("未支持的布局：" + id);
    }

    static final class Configured implements LayoutProfile {
        private final String id;
        private final Geometry geometry;
        private final Map<String, String[]> names, hints, left, right, labels;

        Configured(
                String id,
                Geometry geometry,
                String[][] names,
                String[][] hints,
                String[][] left,
                String[][] right,
                String[][] labels) {
            this.id = id;
            this.geometry = geometry;
            this.names = translations(names);
            this.hints = translations(hints);
            this.left = translations(left);
            this.right = translations(right);
            this.labels = translations(labels);
        }

        private static Map<String, String[]> translations(String[][] rows) {
            Map<String, String[]> values = new LinkedHashMap<>();
            for (String[] row : rows) values.put(row[0], Arrays.copyOfRange(row, 1, row.length));
            return values;
        }

        private static String text(Map<String, String[]> values, Locale locale) {
            String[] result = LocalizedText.choose(values, locale);
            return result == null || result.length == 0 ? "" : result[0];
        }

        public String id() {
            return id;
        }

        public String displayName(Locale locale) {
            return text(names, locale);
        }

        public String installationHint(Locale locale) {
            return text(hints, locale);
        }

        public String directionLabel(boolean reverse, Locale locale) {
            return text(reverse ? right : left, locale);
        }

        public String[] labels(Locale locale) {
            String[] result = LocalizedText.choose(labels, locale);
            return result == null ? new String[0] : result.clone();
        }

        public float[][] outline() {
            return geometry.outline();
        }

        public int[] defaultCounts(int units) {
            return geometry.defaultCounts(units);
        }

        public int[][][] points(int width, int height, int depth) {
            return geometry.points(width, height, depth);
        }
    }
}
