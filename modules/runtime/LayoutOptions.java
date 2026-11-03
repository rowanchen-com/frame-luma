package frameluma.runtime;

import frameluma.core.EdgeLayout;
import frameluma.layouts.*;

import org.json.*;

/** Read-only configuration snapshot. Edits create a new instance through AppCore. */
public final class LayoutOptions {
    public final String mode;
    public final int[] counts;
    public final int offHead, offTail, offset, units;
    public final boolean reverse;
    private final EdgeLayout geometry;

    public LayoutOptions(JSONObject j) throws JSONException {
        mode = j.optString("mode", LayoutCatalog.DEFAULT);
        LayoutProfile profile = LayoutCatalog.require(mode);
        JSONArray a = j.getJSONArray("counts");
        if (a.length() != profile.labels().length)
            throw new IllegalArgumentException("Region counts differ from layout profile");
        counts = new int[a.length()];
        int sum = 0;
        for (int i = 0; i < counts.length; i++) {
            counts[i] = a.getInt(i);
            sum += counts[i];
        }
        offHead = j.optInt("off_head", 0);
        offTail = j.optInt("off_tail", 0);
        offset = j.optInt("offset_units", 0);
        units = j.optInt("unit_count", sum + offHead + offTail);
        reverse = j.optBoolean("reverse", false);
        geometry = new EdgeLayout(units, counts, offHead, offTail, offset, reverse);
    }

    public EdgeLayout geometry() {
        return geometry;
    }

    public LayoutProfile profile() {
        return LayoutCatalog.require(mode);
    }

    public static LayoutOptions defaults(String profileId, int units) throws JSONException {
        LayoutProfile profile = LayoutCatalog.require(profileId);
        return new LayoutOptions(
                new JSONObject()
                        .put("mode", profileId)
                        .put("unit_count", units)
                        .put("counts", new JSONArray(profile.defaultCounts(units))));
    }

    public JSONObject json() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("mode", mode);
        j.put("unit_count", units);
        JSONArray a = new JSONArray();
        for (int n : counts) a.put(n);
        j.put("counts", a);
        j.put("off_head", offHead);
        j.put("off_tail", offTail);
        j.put("offset_units", offset);
        j.put("reverse", reverse);
        return j;
    }
}
