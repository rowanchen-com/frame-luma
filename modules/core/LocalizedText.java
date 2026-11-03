package frameluma.core;

import java.util.Locale;
import java.util.Map;

/** Locale fallback shared by configuration names and layout descriptions. */
public final class LocalizedText {
    private LocalizedText() {}

    public static <T> T choose(Map<String, T> values, Locale locale) {
        String tag = locale.toLanguageTag().replace('_', '-');
        for (String requested : new String[] {tag, locale.getLanguage(), "en", "default"}) {
            for (Map.Entry<String, T> entry : values.entrySet()) {
                if (requested.equalsIgnoreCase(entry.getKey().replace('_', '-')))
                    return entry.getValue();
            }
        }
        return values.isEmpty() ? null : values.values().iterator().next();
    }
}
