package frameluma.core;

/** Map any supported logical region count to physical units; topology belongs to layouts. */
public final class EdgeLayout {
    private final int total, zones;
    private final int[] mapping;

    public EdgeLayout(int total, int[] counts, int head, int tail, int offset, boolean reversed) {
        if (total < 1
                || total > 4096
                || counts == null
                || counts.length < 1
                || counts.length > 64
                || head < 0
                || tail < 0
                || head > total
                || tail > total
                || offset < 0
                || offset >= total) throw new IllegalArgumentException("Invalid physical layout");
        int sum = head + tail;
        for (int value : counts) {
            if (value < 0 || value > total)
                throw new IllegalArgumentException("Invalid region count");
            sum += value;
        }
        if (sum != total) throw new IllegalArgumentException("分区加关闭段必须合计" + total + "，当前" + sum);
        this.total = total;
        this.zones = counts.length;
        mapping = new int[total];
        for (int physical = 0; physical < total; physical++) {
            int unit = (physical - offset + total) % total;
            if (reversed) unit = total - 1 - unit;
            if (unit < head || unit >= total - tail) {
                mapping[physical] = -1;
                continue;
            }
            unit -= head;
            for (int zone = 0; zone < counts.length; zone++) {
                if (unit < counts[zone]) {
                    mapping[physical] = zone;
                    break;
                }
                unit -= counts[zone];
            }
        }
    }

    public int unitCount() {
        return total;
    }

    public int zoneCount() {
        return zones;
    }

    public int zoneAt(int physical) {
        if (physical < 0 || physical >= total)
            throw new IllegalArgumentException("Physical unit out of range");
        return mapping[physical];
    }

    public int[] map(int[] zones) {
        int[] units = new int[total];
        mapInto(zones, units);
        return units;
    }

    /**
     * Caller owns the fixed output buffer; every unit is overwritten, including disabled sections.
     */
    public void mapInto(int[] colors, int[] units) {
        if (colors == null || colors.length != zones)
            throw new IllegalArgumentException("Region colors do not match layout");
        requireOutput(units);
        if (colors == units)
            throw new IllegalArgumentException("Mapping needs a separate output buffer");
        for (int i = 0; i < total; i++) {
            int zone = mapping[i];
            units[i] = zone < 0 ? 0 : colors[zone] & 0xffffff;
        }
    }

    public int[] locate(int oneBasedUnit) {
        int[] units = new int[total];
        locateInto(oneBasedUnit, units);
        return units;
    }

    public void locateInto(int oneBasedUnit, int[] units) {
        if (oneBasedUnit < 1 || oneBasedUnit > total)
            throw new IllegalArgumentException("Invalid locator unit");
        requireOutput(units);
        java.util.Arrays.fill(units, 0);
        units[oneBasedUnit - 1] = 0xffffff;
    }

    public int[] markZone(int zone) {
        int[] units = new int[total];
        markZoneInto(zone, units);
        return units;
    }

    public void markZoneInto(int zone, int[] units) {
        if (zone < 0 || zone >= zones) throw new IllegalArgumentException("Invalid locator region");
        requireOutput(units);
        for (int i = 0; i < total; i++) units[i] = mapping[i] == zone ? 0xffffff : 0;
    }

    private void requireOutput(int[] units) {
        if (units == null || units.length != total)
            throw new IllegalArgumentException("Physical output buffer has wrong length");
    }
}
