package frameluma.devices.philips.strip3;

import frameluma.core.LightCapabilities;
import frameluma.core.LightIdentity;
import frameluma.deviceapi.ModuleConfig;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Validated strip3 PC protocol; lengths are hex and every physical unit is covered. */
final class Frames {
    private Frames() {}

    /** One instance per USB session. Returned bytes include CRLF and live until the next encode. */
    public static final class Encoder {
        private static final byte[] PREFIX = "set_rgb_pc ".getBytes(StandardCharsets.US_ASCII);
        private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        private final byte[] bytes;
        private final LightCapabilities capabilities;
        private final int maximumRun;
        private int at;

        public Encoder(LightCapabilities capabilities, ModuleConfig protocol) {
            this.capabilities = capabilities;
            maximumRun = protocol.integer("maximum_run_units");
            if (maximumRun < 1 || maximumRun > 15)
                throw new IllegalArgumentException("RGB-PC runs use one hex digit");
            bytes = new byte[protocol.integer("maximum_frame_bytes_exclusive")];
            System.arraycopy(PREFIX, 0, bytes, 0, PREFIX.length);
        }

        /** Read-only borrowed buffer; send only the length returned by encode. */
        public byte[] bytes() {
            return bytes;
        }

        public int encode(int[] rgb, int sequence, int brightness) {
            capabilities.requireUnits(rgb == null ? 0 : rgb.length);
            if (brightness < 0 || brightness > 100)
                throw new IllegalArgumentException("Brightness out of range");
            for (int color : rgb)
                if (color < 0 || color > 0xffffff) throw new IllegalArgumentException("RGB range");
            at = PREFIX.length;
            hex(sequence & 65535, 4);
            bytes[at++] = ' ';
            bytes[at++] = '0';
            bytes[at++] = '0';
            bytes[at++] = ' ';
            hex(brightness == 0 ? 1 : brightness, 2);
            for (int start = 0; start < rgb.length; ) {
                int color = brightness == 0 ? 0 : rgb[start], end = start + 1;
                while (end < rgb.length
                        && end - start < maximumRun
                        && (brightness == 0 || rgb[end] == color)) end++;
                run(color, end - start);
                start = end;
            }
            bytes[at++] = '\r';
            bytes[at++] = '\n';
            return at;
        }

        private void run(int color, int length) {
            if (at + 9 + 2 >= bytes.length)
                throw new IllegalArgumentException("Strip frame exceeds verified limit");
            bytes[at++] = ' ';
            hex(color, 6);
            bytes[at++] = ' ';
            hex(length, 1);
        }

        private void hex(int value, int digits) {
            for (int shift = (digits - 1) * 4; shift >= 0; shift -= 4)
                bytes[at++] = HEX[(value >>> shift) & 15];
        }
    }
}

/** Streamed replies may cross USB packets. Keep only one bounded incomplete line. */
final class Replies {
    private final StringBuilder line = new StringBuilder(128);
    private String failure;

    static LightIdentity identity(String modelReply, String expectedModel) {
        return new LightIdentity(verifiedLine(modelReply, expectedModel));
    }

    /**
     * Decode a complete scalar first, then admit lengths from the controller's one capability
     * source.
     */
    static int length(String response, int[] supportedLengths)
            throws frameluma.core.UnsupportedLightException {
        for (String line : response.split("[\\r\\n]+"))
            if (line.trim().matches("[0-9]+")) {
                int length = Integer.parseInt(line.trim());
                for (int supported : supportedLengths) if (length == supported) return length;
                throw new frameluma.core.UnsupportedLightException(length + "米灯带尚未适配，已暂停重试");
            }
        throw new IllegalArgumentException("No length reply");
    }

    private static String verifiedLine(String response, String expected) {
        for (String line : response.split("[\\r\\n]+"))
            if (expected.equals(line.trim())) return line.trim();
        throw new IllegalArgumentException("Unexpected controller model");
    }

    static boolean complete(String command, String response, String expectedModel) {
        for (String line : response.split("(?<=\\n)")) {
            if (!line.endsWith("\n")) continue;
            String value = line.trim();
            if (value.toLowerCase(Locale.ROOT).contains("err")) return true;
            if (command.startsWith("set_") && "ok".equals(value)) return true;
            if (command.startsWith("get_") && value.matches("[0-9]+")) return true;
            if ("model".equals(command) && expectedModel.equals(value)) return true;
        }
        return false;
    }

    String accept(byte[] bytes, int count) {
        for (int index = 0; index < count; index++) {
            char value = (char) (bytes[index] & 255);
            if (value == '\r' || value == '\n') {
                if (line.toString().toLowerCase(Locale.ROOT).contains("err"))
                    failure = line.toString();
                line.setLength(0);
            } else if (line.length() < 2048) {
                line.append(value);
            } else {
                failure = "Strip reply exceeds bounded line length";
                line.setLength(0);
            }
        }
        return failure;
    }
}

/**
 * One serial owner; every completed frame grants the controller its minimum processing interval.
 */
final class FrameInterval {
    private final int minimum;
    private long previous = -1;

    FrameInterval(int minimum) {
        if (minimum < 0) throw new IllegalArgumentException("Negative interval");
        this.minimum = minimum;
    }

    long waitMs(long now) {
        return previous < 0 || now < previous ? 0 : Math.max(0, minimum - (now - previous));
    }

    void completed(long now) {
        previous = now;
    }
}
