package it.eng.dome.revenue.engine.utils;

import java.util.UUID;

public class IdUtils {

    private static final String URN_PREFIX = "urn:ngsi-ld:";

    private static final String DELIMITER = "Z";

    public static String pack(String entityType, String... parts) {
        StringBuilder sb = new StringBuilder();
        sb.append(URN_PREFIX).append(entityType).append(":");
        for (int i = 0; i < parts.length; i++) {
            sb.append(escape(parts[i]));
            if (i < parts.length - 1) {
                sb.append(DELIMITER); //delimiter
            }
        }
        return sb.toString();
    }

    public static String[] unpack(String id, String expectedEntityType) {
        if (!id.startsWith(URN_PREFIX + expectedEntityType + ":")) {
            throw new IllegalArgumentException("Not valid ID for type:  " + expectedEntityType + ": " + id);
        }
        String body = id.substring((URN_PREFIX + expectedEntityType + ":").length());
//        String[] parts = body.split("\\|");
        String[] parts = body.split(DELIMITER);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = unescape(parts[i]);
        }
        return parts;
    }

    private static String escape(String value) {
        return value.replace("|", "%7C");
    }

    private static String unescape(String value) {
        return value.replace("%7C", "|");
    }

    public static String uuidFromKey(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes()).toString();
    }
}