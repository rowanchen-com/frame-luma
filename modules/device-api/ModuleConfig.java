package frameluma.deviceapi;

import android.content.Context;

import frameluma.core.CaptureCapabilities;
import frameluma.core.LocalizedText;
import frameluma.core.UiRenderingProfile;
import frameluma.core.Verification;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One adapter's immutable configuration, injected by the generated registry. */
public final class ModuleConfig {
    private static final Map<String, ModuleConfig> CACHE = new ConcurrentHashMap<>();
    private final JSONObject data;

    public static ModuleConfig load(Context context, String id) {
        return CACHE.computeIfAbsent(
                id,
                key -> {
                    try {
                        Context owner =
                                OwnerIdentity.PACKAGE.equals(context.getPackageName())
                                        ? context
                                        : context.createPackageContext(OwnerIdentity.PACKAGE, 0);
                        try (InputStream input =
                                        owner.getAssets().open("modules/" + key + "/config.json");
                                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[2048];
                            int size;
                            while ((size = input.read(buffer)) >= 0) output.write(buffer, 0, size);
                            ModuleConfig config = new ModuleConfig(output.toString("UTF-8"));
                            if (!key.equals(config.string("id")))
                                throw new IllegalArgumentException("Adapter id mismatch");
                            return config;
                        }
                    } catch (Exception error) {
                        throw new IllegalStateException("Unable to load adapter " + key, error);
                    }
                });
    }

    public ModuleConfig(String json) {
        try {
            data = new JSONObject(json);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid adapter configuration", error);
        }
    }

    private ModuleConfig(JSONObject data) {
        this.data = data;
    }

    public String string(String key) {
        try {
            return data.getString(key);
        } catch (JSONException error) {
            throw missing(key, error);
        }
    }

    public String string(String key, String fallback) {
        return data.optString(key, fallback);
    }

    public int integer(String key) {
        try {
            return data.getInt(key);
        } catch (JSONException error) {
            throw missing(key, error);
        }
    }

    public int integer(String key, int fallback) {
        return data.optInt(key, fallback);
    }

    public ModuleConfig section(String key) {
        try {
            return new ModuleConfig(data.getJSONObject(key));
        } catch (JSONException error) {
            throw missing(key, error);
        }
    }

    public String[] strings(String key) {
        JSONArray values = data.optJSONArray(key);
        String[] result = new String[values == null ? 0 : values.length()];
        for (int index = 0; index < result.length; index++) result[index] = values.optString(index);
        return result;
    }

    public int[] integers(String key) {
        try {
            JSONArray values = data.getJSONArray(key);
            int[] result = new int[values.length()];
            for (int index = 0; index < result.length; index++)
                result[index] = values.getInt(index);
            return result;
        } catch (JSONException error) {
            throw missing(key, error);
        }
    }

    public String text(String key) {
        return text(key, Locale.getDefault());
    }

    public String text(String key, Locale locale) {
        JSONObject translations = data.optJSONObject(key);
        if (translations == null) return string(key);
        Map<String, String> values = new LinkedHashMap<>();
        for (Iterator<String> keys = translations.keys(); keys.hasNext(); ) {
            String tag = keys.next();
            values.put(tag, translations.optString(tag));
        }
        String text = LocalizedText.choose(values, locale);
        return text == null ? "" : text;
    }

    public int matchScore(DeviceModule.Info device) {
        ModuleConfig detection = section("detection");
        int score = detection.integer("priority", 0);
        for (String model : detection.strings("model_hints")) {
            if (normalize(model).equals(normalize(device.model))) {
                score += 100;
                break;
            }
        }
        for (String brand : detection.strings("brand_hints")) {
            if (normalize(brand).equals(normalize(device.brand))
                    || normalize(brand).equals(normalize(device.manufacturer))) {
                score += 10;
                break;
            }
        }
        return score;
    }

    public CaptureCapabilities capture() {
        ModuleConfig capabilities = section("capabilities");
        ModuleConfig strategy = capabilities.section("sampling_strategy");
        JSONObject inputData = capabilities.section("inputs").data;
        Map<String, Verification> inputs = new LinkedHashMap<>();
        for (Iterator<String> keys = inputData.keys(); keys.hasNext(); ) {
            String name = keys.next();
            inputs.put(
                    name, Verification.valueOf(inputData.optString(name).toUpperCase(Locale.ROOT)));
        }
        return new CaptureCapabilities(
                CaptureCapabilities.Mode.valueOf(
                        capabilities.string("capture_mode").toUpperCase(Locale.ROOT)),
                capabilities.integer("panel_width"),
                capabilities.integer("panel_height"),
                inputs,
                strategy.integer("edge_depth_percent"),
                strategy.integer("regions_per_update"));
    }

    public UiRenderingProfile rendering() {
        return UiRenderingProfile.DEFAULT.withVideoSurface(
                UiRenderingProfile.VideoSurface.valueOf(
                        section("ui_rendering").string("video_surface").toUpperCase(Locale.ROOT)));
    }

    /** Defensive snapshot for a platform transport; called only when a connection is opened. */
    public JSONObject json() {
        try {
            return new JSONObject(data.toString());
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('_', '-').toUpperCase(Locale.ROOT);
    }

    private static IllegalArgumentException missing(String key, Exception error) {
        return new IllegalArgumentException("Missing adapter parameter: " + key, error);
    }
}
