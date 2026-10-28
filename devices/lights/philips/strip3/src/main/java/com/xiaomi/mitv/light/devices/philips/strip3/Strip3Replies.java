package com.xiaomi.mitv.light.devices.philips.strip3;

import java.util.Locale;
import com.xiaomi.mitv.light.core.LightIdentity;

/** Streamed replies may cross USB packets. Keep only one bounded incomplete line. */
final class Strip3Replies {
    private final StringBuilder line = new StringBuilder(128);
    private String failure;

    static LightIdentity identity(String modelReply,String firmwareReply) {
        return new LightIdentity(verifiedLine(modelReply,"philips.light.strip3"),verifiedLine(firmwareReply,"2.1.8_0039"));
    }
    private static String verifiedLine(String response,String expected) {
        for(String line:response.split("[\\r\\n]+"))if(expected.equals(line.trim()))return line.trim();
        throw new IllegalArgumentException("Unexpected controller model/firmware");
    }

    static boolean complete(String command, String response) {
        for(String line:response.split("(?<=\\n)")) {
            if(!line.endsWith("\n"))continue;
            String value=line.trim();
            if(value.toLowerCase(Locale.ROOT).contains("err"))return true;
            if(command.startsWith("set_")&&"ok".equals(value))return true;
            if(command.startsWith("get_")&&value.matches("[0-9]+"))return true;
            if("model".equals(command)&&"philips.light.strip3".equals(value))return true;
            if("version".equals(command)&&"2.1.8_0039".equals(value))return true;
        }
        return false;
    }

    String accept(byte[] bytes, int count) {
        for (int index = 0; index < count; index++) {
            char value = (char) (bytes[index] & 255);
            if (value == '\r' || value == '\n') {
                if (line.toString().toLowerCase(Locale.ROOT).contains("err")) failure = line.toString();
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
