package it.overzoom.ordinainchat.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtil {

    private String guessProductNameFromText(String raw) {
        if (raw == null)
            return null;

        // Normalizza in minuscolo
        String lower = raw.toLowerCase(Locale.ITALY);

        // Cerca pattern comuni tipo "delle cozze pelose", "dei fasolari", "di spigole"
        Pattern p = Pattern.compile("\\b(?:delle|dei|del|di)\\s+([a-zà-ù]+(?:\\s+[a-zà-ù]+)*)");
        Matcher m = p.matcher(lower);
        if (m.find()) {
            return m.group(1).trim();
        }

        // Fallback: cerca parole chiave tipiche di prodotti (cozze, spigole, triglie,
        // ecc.)
        List<String> prodottiKnown = List.of("cozze pelose", "fasolari", "spigole", "orate", "triglie", "ostriche");
        for (String prod : prodottiKnown) {
            if (lower.contains(prod)) {
                return prod;
            }
        }

        return null; // non trovato
    }

}
