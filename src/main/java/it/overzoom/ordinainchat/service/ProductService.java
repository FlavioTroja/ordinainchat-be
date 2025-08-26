package it.overzoom.ordinainchat.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.overzoom.ordinainchat.data.ProductNameCache;
import it.overzoom.ordinainchat.util.JsonUtils;
import it.overzoom.ordinainchat.util.TextUtils;

@Service
public class ProductService {
    private final ObjectMapper om = new ObjectMapper();
    private final McpClient mcp;
    private final ProductNameCache cache = new ProductNameCache();

    public ProductService(McpClient mcp) {
        this.mcp = mcp;
    }

    public Map<Long, String> parseIdNameMap(String json) throws Exception {
        JsonNode root = om.readTree(json == null ? "{}" : json);
        Map<Long, String> out = new HashMap<>();
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
            String name = Optional.ofNullable(JsonUtils.textOrNull(p, "name", "productName", "title", "label"))
                    .orElse("");
            if (id > 0 && !name.isBlank())
                out.put(id, name.trim().replaceAll("\\s+", " "));
        }
        return out;
    }

    public String resolveName(long productId, String telegramUserId) {
        if (productId <= 0)
            return null;
        String cached = cache.get(productId);
        if (cached != null && !cached.isBlank())
            return cached;

        try {
            ObjectNode args = om.createObjectNode().put("id", productId);
            ObjectNode payload = om.createObjectNode();
            payload.put("tool", "products_byid");
            payload.set("arguments", args);
            ObjectNode meta = om.createObjectNode();
            if (telegramUserId != null && !telegramUserId.isBlank())
                meta.put("telegramUserId", telegramUserId);
            payload.set("meta", meta);

            String body = mcp.call(payload);
            JsonNode root = om.readTree(body == null ? "{}" : body);
            JsonNode data = root.path("data");
            if (data.isMissingNode())
                data = root;

            String name = Optional.ofNullable(JsonUtils.textOrNull(data, "name", "productName", "title", "label"))
                    .orElse("");
            if (!name.isBlank()) {
                name = name.trim().replaceAll("\\s+", " ");
                cache.put(productId, name);
                return name;
            }
        } catch (Exception ignored) {
        }
        return cached;
    }

    public JsonNode pickBestMatch(JsonNode items, String desiredName) {
        double bestScore = -1.0;
        JsonNode best = null;
        for (JsonNode p : items) {
            String name = JsonUtils.safeTxt(p, "name");
            if (name.isBlank())
                continue;
            double s = TextUtils.similarity(name, desiredName);
            if (s > bestScore) {
                bestScore = s;
                best = p;
            }
            if (s >= 0.999)
                return p;
        }
        return best;
    }

    public void cacheIdNameMap(Map<Long, String> map) {
        cache.putAll(map);
    }

    public void cacheName(long id, String name) {
        cache.put(id, name);
    }
}
