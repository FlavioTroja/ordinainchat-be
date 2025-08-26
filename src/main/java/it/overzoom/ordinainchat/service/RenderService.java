package it.overzoom.ordinainchat.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.overzoom.ordinainchat.util.JsonUtils;

@Service
public class RenderService {
    private final ObjectMapper om = new ObjectMapper();

    public String productsList(String mcpResponseBody, String heading,
            java.util.function.BiConsumer<Long, String> cachePut) {
        try {
            JsonNode root = om.readTree(mcpResponseBody == null ? "{}" : mcpResponseBody);
            if ("error".equalsIgnoreCase(root.path("status").asText(""))) {
                String msg = root.path("message").asText("Errore MCP");
                return "Errore dal gestionale: " + msg;
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode())
                data = root;
            JsonNode items = data.path("items");
            if (!items.isArray() || items.size() == 0)
                return "Al momento non risultano articoli disponibili per la ricerca richiesta.";

            StringBuilder sb = new StringBuilder(heading).append("\n");
            for (JsonNode p : items) {
                long id = p.path("id").asLong(0L);
                String name = JsonUtils.safeTxt(p, "name");
                if (id > 0 && !name.isBlank() && cachePut != null)
                    cachePut.accept(id, name.trim().replaceAll("\\s+", " "));
                String desc = JsonUtils.safeTxt(p, "description");
                String price = JsonUtils.formatPrice(p.path("priceEur"), java.util.Locale.ITALY);
                String freshness = JsonUtils.safeTxt(p, "freshness");
                String source = JsonUtils.safeTxt(p, "source");

                List<String> extra = new ArrayList<>();
                String originArea = JsonUtils.safeTxt(p, "originArea");
                String faoArea = JsonUtils.safeTxt(p, "faoArea");
                String originCountry = JsonUtils.safeTxt(p, "originCountry");
                if (!originArea.isBlank())
                    extra.add(originArea);
                if (!faoArea.isBlank())
                    extra.add("FAO " + faoArea);
                if (!originCountry.isBlank())
                    extra.add(originCountry);

                sb.append("• ").append(name);
                if (!desc.isBlank())
                    sb.append(" — ").append(desc);
                if (!price.isBlank())
                    sb.append(" — ").append(price).append("/kg");

                List<String> tags = new ArrayList<>();
                if (freshness.equalsIgnoreCase("FRESH"))
                    tags.add("fresco");
                if (freshness.equalsIgnoreCase("FROZEN"))
                    tags.add("surgelato");
                if (source.equalsIgnoreCase("WILD_CAUGHT"))
                    tags.add("pescato");
                if (source.equalsIgnoreCase("FARMED"))
                    tags.add("allevato");
                if (!tags.isEmpty())
                    sb.append(" (").append(String.join(", ", tags)).append(")");

                if (!extra.isEmpty())
                    sb.append(" — ").append(String.join(", ", extra));
                sb.append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Errore nel parsing della risposta dal gestionale.";
        }
    }

    public String productDetail(String mcpResponseBody) {
        try {
            JsonNode root = om.readTree(mcpResponseBody == null ? "{}" : mcpResponseBody);
            JsonNode d = root.path("data");
            if (d.isMissingNode())
                d = root;
            String name = d.path("name").asText("");
            String price = d.path("priceEur").asText("");
            String freshness = d.path("freshness").asText("");
            String source = d.path("source").asText("");
            String origin = d.path("originArea").asText("");

            StringBuilder sb = new StringBuilder();
            if (!name.isBlank())
                sb.append(name);
            if (!price.isBlank())
                sb.append(" — € ").append(price.replace('.', ',')).append("/kg");
            if (!freshness.isBlank() || !source.isBlank()) {
                sb.append(" (");
                if ("FRESH".equalsIgnoreCase(freshness))
                    sb.append("fresco");
                else if ("FROZEN".equalsIgnoreCase(freshness))
                    sb.append("surgelato");
                if (!source.isBlank()) {
                    if (sb.charAt(sb.length() - 1) != '(')
                        sb.append(", ");
                    sb.append("WILD_CAUGHT".equalsIgnoreCase(source) ? "pescato"
                            : "FARMED".equalsIgnoreCase(source) ? "allevato"
                                    : source.toLowerCase(java.util.Locale.ITALY));
                }
                sb.append(")");
            }
            if (!origin.isBlank())
                sb.append(" — ").append(origin);
            String out = sb.toString().trim();
            return out.isEmpty() ? "Dettaglio prodotto non disponibile." : out;
        } catch (Exception e) {
            return "Dettaglio prodotto non disponibile.";
        }
    }

    public String customerProfile(String mcpResponseBody) {
        try {
            JsonNode root = om.readTree(mcpResponseBody == null ? "{}" : mcpResponseBody);
            JsonNode d = root.path("data");
            if (d.isMissingNode())
                d = root;
            String name = d.path("name").asText("");
            String phone = d.path("phone").asText("");
            String out = "Profilo cliente aggiornato.";
            if (!name.isBlank())
                out += " Nome: " + name + ".";
            if (!phone.isBlank())
                out += " Telefono: " + phone + ".";
            return out;
        } catch (Exception e) {
            return "Profilo cliente aggiornato.";
        }
    }
}
