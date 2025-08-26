package it.overzoom.ordinainchat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.overzoom.ordinainchat.util.TextUtils;
import jakarta.annotation.PostConstruct;

@Service
public class ProductCatalogService {

    private final ObjectMapper om = new ObjectMapper();
    private final McpService mcpService;

    // indici
    private final Map<Long, String> idToName = new ConcurrentHashMap<>();
    private final Map<String, Long> normNameToId = new ConcurrentHashMap<>();

    public ProductCatalogService(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @PostConstruct
    public void init() {
        refreshProducts();
    }

    /** Aggiorna l’intero catalogo (es. ogni 2.5 minuti). */
    @Scheduled(fixedDelay = 150_000)
    public void refreshProducts() {
        try {
            ObjectNode payload = om.createObjectNode();
            payload.put("tool", "products_search");
            ObjectNode args = om.createObjectNode();
            args.put("onlyOnOffer", false);
            args.put("page", 0);
            args.put("size", 5000); // alto per coprire l’intero db
            payload.set("arguments", args);

            String body = mcpService.callMcp(payload);
            List<Item> items = parseIdNameList(body);

            rebuildIndexes(items);
        } catch (Exception e) {
            // logga, ma non esplodere
            e.printStackTrace();
        }
    }

    /** Ingest di una PO di search/ordine per aggiornare subito gli indici. */
    public void ingestFromMcp(String mcpBody) {
        try {
            List<Item> items = parseIdNameList(mcpBody);
            if (!items.isEmpty())
                addOrUpdate(items);
        } catch (Exception ignore) {
        }
    }

    /** Lookup name by id (display). */
    public String getNameById(long id) {
        return idToName.get(id);
    }

    /** Lookup id by nome (exact/normalize). */
    public Long getIdByNameExact(String name) {
        if (name == null)
            return null;
        return normNameToId.get(TextUtils.normalize(name));
    }

    public Long getIdByNameFuzzy(String name, double minScore) {
        if (name == null)
            return null;
        String desired = TextUtils.normalize(name);
        Long exact = normNameToId.get(desired);
        if (exact != null)
            return exact;

        double best = -1;
        Long bestId = null;
        for (Map.Entry<Long, String> e : idToName.entrySet()) {
            double s = TextUtils.similarity(e.getValue(), name);
            if (s > best) {
                best = s;
                bestId = e.getKey();
            }
            if (s >= 0.999)
                return e.getKey();
        }
        return (best >= minScore) ? bestId : null;
    }

    public void addOrUpdate(List<Item> items) {
        for (Item it : items) {
            if (it.id() <= 0 || it.name() == null || it.name().isBlank())
                continue;
            String display = it.name().trim().replaceAll("\\s+", " ");
            String norm = TextUtils.normalize(display);
            idToName.put(it.id(), display);
            normNameToId.put(norm, it.id());
        }
    }

    // ---------- parsing & indexing ----------

    public record Item(long id, String name) {
    }

    private List<Item> parseIdNameList(String json) throws Exception {
        List<Item> out = new ArrayList<>();
        JsonNode root = om.readTree(json == null ? "{}" : json);
        JsonNode data = root.path("data");
        if (data.isMissingNode())
            data = root;

        JsonNode items = data.path("items");
        if (!items.isArray()) {
            if (data.isArray())
                items = data;
            else if (root.path("items").isArray())
                items = root.path("items");
            else
                return out;
        }

        for (JsonNode p : items) {
            long id = p.path("id").asLong(0L);
            String name = firstNonBlank(
                    textOrNull(p, "name"),
                    textOrNull(p, "productName"),
                    textOrNull(p, "title"),
                    textOrNull(p, "label"));
            if (id > 0 && name != null && !name.isBlank()) {
                out.add(new Item(id, name));
            }
        }
        return out;
    }

    private void rebuildIndexes(List<Item> items) {
        if (items == null)
            return;
        // ricostruisci da zero (evita residui)
        Map<Long, String> newIdToName = new ConcurrentHashMap<>();
        Map<String, Long> newNormToId = new ConcurrentHashMap<>();
        for (Item it : items) {
            String display = it.name().trim().replaceAll("\\s+", " ");
            String norm = TextUtils.normalize(display);
            newIdToName.put(it.id(), display);
            newNormToId.put(norm, it.id());
        }
        idToName.clear();
        idToName.putAll(newIdToName);
        normNameToId.clear();
        normNameToId.putAll(newNormToId);
    }

    private String textOrNull(JsonNode n, String key) {
        return (n != null && n.hasNonNull(key)) ? n.get(key).asText() : null;
    }

    private String firstNonBlank(String... vals) {
        if (vals == null)
            return null;
        for (String v : vals)
            if (v != null && !v.isBlank())
                return v;
        return null;
    }
}
