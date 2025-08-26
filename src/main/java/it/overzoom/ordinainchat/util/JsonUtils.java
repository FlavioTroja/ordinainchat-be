package it.overzoom.ordinainchat.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {
    private JsonUtils() {
    }

    private static final Pattern TOOL_JSON = Pattern.compile("\\{\\s*\"tool\"\\s*:\\s*\"[^\"]+\"[\\s\\S]*?\\}",
            Pattern.MULTILINE);

    public static JsonNode safeParseAction(ObjectMapper om, String raw) {
        if (raw == null)
            return null;
        String s = raw.trim();

        if (s.startsWith("```")) {
            int first = s.indexOf("```");
            int last = s.lastIndexOf("```");
            if (last > first)
                s = s.substring(first + 3, last).trim();
        }

        Matcher m = TOOL_JSON.matcher(s);
        if (m.find()) {
            try {
                return om.readTree(m.group());
            } catch (Exception ignore) {
            }
        }
        try {
            return om.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static String textOrNull(JsonNode n, String... keys) {
        for (String k : keys)
            if (n.hasNonNull(k))
                return n.get(k).asText();
        return null;
    }

    public static boolean boolOr(JsonNode n, String key, boolean dflt) {
        return n.hasNonNull(key) ? n.get(key).asBoolean(dflt) : dflt;
    }

    public static int intOr(JsonNode n, String key, int dflt) {
        return n.hasNonNull(key) ? n.get(key).asInt(dflt) : dflt;
    }

    public static java.math.BigDecimal decimalOrNull(JsonNode n, String key) {
        if (!n.hasNonNull(key))
            return null;
        try {
            return new java.math.BigDecimal(n.get(key).asText());
        } catch (Exception e) {
            return null;
        }
    }

    public static String safeTxt(JsonNode n, String key) {
        if (!n.hasNonNull(key))
            return "";
        String v = n.get(key).asText("");
        return v == null ? "" : v.trim();
    }

    public static String formatPrice(JsonNode n, java.util.Locale locale) {
        if (n == null || n.isMissingNode() || n.isNull())
            return "";
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(n.asText());
            return String.format(locale, "€ %.2f", bd);
        } catch (Exception e) {
            return "";
        }
    }
}
