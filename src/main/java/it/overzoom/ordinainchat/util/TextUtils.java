package it.overzoom.ordinainchat.util;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class TextUtils {
    private TextUtils() {
    }

    public static String normalize(String s) {
        if (s == null)
            return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        n = n.toLowerCase(Locale.ITALY)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }

    public static String capitalizeWords(String s) {
        if (s == null || s.isBlank())
            return s;
        String[] parts = s.trim().replaceAll("\\s+", " ").split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty())
                continue;
            out.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.length() > 1 ? p.substring(1) : "")
                    .append(" ");
        }
        return out.toString().trim();
    }

    private static Set<String> tokens(String s) {
        String n = normalize(s);
        Set<String> out = new LinkedHashSet<>();
        for (String t : n.split("\\s+"))
            if (!t.isBlank())
                out.add(t);
        return out;
    }

    /** Similarità semplice token-based (0..1). */
    public static double similarity(String a, String b) {
        String na = normalize(a), nb = normalize(b);
        if (na.equals(nb))
            return 1.0;

        Set<String> ta = tokens(na), tb = tokens(nb);
        if (!tb.isEmpty() && ta.containsAll(tb))
            return 0.9;

        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        double j = union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
        if (j > 0)
            return 0.5 + 0.4 * j;

        if (na.contains(nb) || nb.contains(na))
            return 0.4;
        return 0.0;
    }

    public static BigDecimal parseQuantityKg(String s) {
        if (s == null)
            return null;
        String t = s.toLowerCase(Locale.ITALY).trim()
                .replaceAll("[^\\p{L}\\p{N}\\s\\.,/]", " ")
                .replaceAll("\\s+", " ").trim();

        t = t.replace("mezzo", "0,5").replace("mezza", "0,5")
                .replace("un chilo", "1").replace("uno chilo", "1")
                .replace("un kilo", "1").replace("uno kilo", "1")
                .replace("un mezzo", "0,5").replace("mezzetto", "0,5");

        java.util.regex.Matcher frac = java.util.regex.Pattern
                .compile("(\\d+)\\s*/\\s*(\\d+)").matcher(t);
        if (frac.find()) {
            try {
                BigDecimal num = new BigDecimal(frac.group(1));
                BigDecimal den = new BigDecimal(frac.group(2));
                BigDecimal val = num.divide(den, 3, java.math.RoundingMode.HALF_UP);
                t = frac.replaceFirst(val.toPlainString().replace('.', ','));
            } catch (Exception ignored) {
            }
        }

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:[\\.,]\\d+)?)").matcher(t);
        if (!m.find())
            return null;

        String numStr = m.group(1).replace('.', ',');
        BigDecimal qty;
        try {
            qty = new BigDecimal(numStr.replace(',', '.'));
        } catch (Exception e) {
            return null;
        }

        boolean hasKg = t.contains("kg") || t.contains("chilo") || t.contains("kilo") || t.contains("chili")
                || t.contains("kili");
        boolean hasEtti = t.contains("etto") || t.contains("etti") || t.contains("hg");
        boolean hasGrammi = t.contains("grammi") || t.contains("gr") || t.contains("g");

        if (hasEtti)
            qty = qty.multiply(new BigDecimal("0.1"));
        else if (hasGrammi)
            qty = qty.divide(new BigDecimal("1000"), 3, java.math.RoundingMode.HALF_UP);

        if (qty.compareTo(BigDecimal.ZERO) <= 0)
            return null;
        return qty.setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
    }

    public static String toPlainText(String s) {
        if (s == null)
            return "";
        return s.replace("*", "").replace("_", "").replace("`", "").trim();
    }
}
