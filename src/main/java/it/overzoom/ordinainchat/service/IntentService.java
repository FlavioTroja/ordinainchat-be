package it.overzoom.ordinainchat.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class IntentService {
    private final ObjectMapper om = new ObjectMapper();

    public ObjectNode buildProductsSearchArgsFromImplicitIntent(String lower) {
        ObjectNode args = om.createObjectNode();
        boolean askFresh = lower.contains("fresco") || lower.contains("di fresco");
        boolean askOffer = lower.contains("offerta") || lower.contains("in offerta") || lower.contains("promo");
        boolean askLocal = lower.contains("locale") || lower.contains("puglia") || lower.contains("pugliese")
                || lower.contains("adriatico") || lower.contains("ionio") || lower.contains("italia")
                || lower.contains("italiano");

        if (askFresh)
            args.put("freshness", "FRESH");
        if (askOffer)
            args.put("onlyOnOffer", true);
        if (askLocal) {
            args.put("originCountry", "IT");
            args.put("faoAreaPrefix", "37.2");
            args.put("originAreaLike", "puglia|adriatico|ionio");
            args.put("landingPortLike", "bari|brindisi|taranto|manfredonia|monopoli|molfetta|trani|barletta|gallipoli");
            if (!args.has("freshness"))
                args.put("freshness", "FRESH");
            args.put("source", "WILD_CAUGHT");
        }

        // se non ci sono filtri espliciti, uso il testo come query prodotto
        // if (!askFresh && !askOffer && !askLocal) {
        // args.put("query", lower);
        // }

        args.put("page", 0);
        args.put("size", 10);
        return args;
    }

    public String headingForImplicitIntent(String lower) {
        boolean askFresh = lower.contains("fresco") || lower.contains("di fresco");
        boolean askOffer = lower.contains("offerta") || lower.contains("in offerta") || lower.contains("promo");
        boolean askLocal = lower.contains("locale") || lower.contains("puglia") || lower.contains("pugliese")
                || lower.contains("adriatico") || lower.contains("ionio") || lower.contains("italia")
                || lower.contains("italiano");
        if (askFresh && askOffer)
            return "Ecco il fresco in offerta:";
        if (askFresh)
            return "Oggi fresco disponibile:";
        if (askOffer)
            return "Ecco le offerte disponibili:";
        if (askLocal)
            return "Prodotti locali disponibili:";
        return "Risultati disponibili:";
    }
}
