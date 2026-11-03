package frameluma.core;

/** Verified output limits; controller lengths are not properties of the display platform. */
public final class LightCapabilities {
    private final int[] units;

    public LightCapabilities(int... supportedUnits) {
        if (supportedUnits == null || supportedUnits.length == 0)
            throw new IllegalArgumentException("Missing output lengths");
        units = supportedUnits.clone();
        for (int value : units)
            if (value < 1 || value > 4096)
                throw new IllegalArgumentException("Invalid output length");
    }

    public void requireUnits(int count) {
        for (int value : units) if (value == count) return;
        throw new IllegalArgumentException("控制盒尚未验证此段数：" + count);
    }

    public int[] supportedUnits() {
        return units.clone();
    }
}
