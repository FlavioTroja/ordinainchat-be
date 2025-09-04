package it.overzoom.ordinainchat.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.overzoom.ordinainchat.util.JsonUtils;
import it.overzoom.ordinainchat.util.TextUtils;

@Service
public class OrderService {
    private final ObjectMapper om = new ObjectMapper();
    private final McpClient mcp;
    private final ProductService products;

    public OrderService(McpClient mcp, ProductService products) {
        this.mcp = mcp;
        this.products = products;
    }

    public ObjectNode sanitizeOrdersCreateArgs(ObjectNode modelArgs, String telegramUserId, String candidateFromCtx) {
        ObjectNode out = om.createObjectNode();
        ArrayNode cleanItems = om.createArrayNode();

        JsonNode items = modelArgs.path("items");
        if (!items.isArray())
            items = om.createArrayNode().add(modelArgs);

        for (JsonNode it : items) {
            BigDecimal qty = parseQty(it.path("quantity").asText(null));
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
                continue;

            long pid = it.path("productId").asLong(0L);
            String resolvedName = null;
            if (pid > 0) {
                resolvedName = products.resolveName(pid, telegramUserId);
                if (isBlank(resolvedName))
                    pid = 0L;
            }

            if (pid == 0L) {
                String candidateName = firstNonBlank(
                        JsonUtils.textOrNull(it, "name", "productName", "textSearch"),
                        candidateFromCtx);
                if (isBlank(candidateName))
                    continue;

                // search e best-match
                try {
                    ObjectNode searchArgs = om.createObjectNode();
                    searchArgs.put("textSearch", candidateName);
                    searchArgs.put("page", 0);
                    searchArgs.put("size", 10);
                    ObjectNode meta = om.createObjectNode().put("telegramUserId", telegramUserId);
                    ObjectNode payload = om.createObjectNode();
                    payload.put("tool", "products_search");
                    payload.set("arguments", searchArgs);
                    payload.set("meta", meta);

                    String body = mcp.call(payload);
                    JsonNode root = om.readTree(body == null ? "{}" : body);
                    JsonNode data = root.path("data");
                    if (data.isMissingNode())
                        data = root;
                    JsonNode list = data.path("items");
                    if (list.isArray() && list.size() > 0) {
                        JsonNode best = products.pickBestMatch(list, candidateName);
                        if (best != null) {
                            pid = best.path("id").asLong(0L);
                            String bestName = JsonUtils.safeTxt(best, "name");
                            if (pid > 0 && !bestName.isBlank()) {
                                products.cacheName(pid, bestName.trim().replaceAll("\\s+", " "));
                                resolvedName = bestName;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (pid <= 0)
                continue;

            ObjectNode clean = om.createObjectNode();
            clean.put("productId", pid);
            clean.put("quantity", qty);
            if (it.hasNonNull("priceKg"))
                clean.put("priceKg", it.get("priceKg").decimalValue());
            if (it.hasNonNull("pricePiece"))
                clean.put("pricePiece", it.get("pricePiece").decimalValue());
            if (it.hasNonNull("priceEur"))
                clean.put("priceEur", it.get("priceEur").decimalValue());
            cleanItems.add(clean);
        }
        out.set("items", cleanItems);
        return out;
    }

    public String renderOrderConfirmation(String mcpResponseBody, ObjectNode argsSent, String telegramUserId) {
        try {
            JsonNode root = om.readTree(mcpResponseBody == null ? "{}" : mcpResponseBody);
            if ("error".equalsIgnoreCase(root.path("status").asText(""))) {
                String msg = root.path("message").asText("Errore MCP");
                return "Errore nel creare l’ordine: " + msg;
            }
            String orderId = firstNonBlank(
                    root.path("data").path("orderId").asText(""),
                    root.path("orderId").asText(""));

            Map<Long, String> namesFromOrder = new HashMap<>();
            JsonNode dataItems = root.path("data").path("items");
            if (dataItems.isArray()) {
                for (JsonNode it : dataItems) {
                    long pid = it.path("productId").asLong(0L);
                    String n = firstNonBlank(
                            it.path("name").asText(null),
                            it.path("productName").asText(null),
                            it.path("title").asText(null));
                    if (pid > 0 && n != null && !n.isBlank()) {
                        n = n.trim().replaceAll("\\s+", " ");
                        namesFromOrder.put(pid, n);
                        products.cacheName(pid, n);
                    }
                }
            }

            StringBuilder itemsTxt = new StringBuilder();
            JsonNode items = argsSent.path("items");
            if (items.isArray()) {
                for (JsonNode it : items) {
                    long pid = it.path("productId").asLong(0L);
                    String qty = it.path("quantity").asText("").replace('.', ',');
                    String name = namesFromOrder.get(pid);
                    if (isBlank(name))
                        name = products.resolveName(pid, telegramUserId);
                    if (isBlank(name))
                        name = "articolo";
                    if (itemsTxt.length() > 0)
                        itemsTxt.append(", ");
                    String unit = it.hasNonNull("priceKg") ? "kg" : "pz";
                    itemsTxt.append(qty).append(" ").append(unit).append(" di ")
                            .append(TextUtils.capitalizeWords(name));
                }
            }
            String base = "Ordine registrato: " + (itemsTxt.length() == 0 ? "articoli" : itemsTxt) + ".";
            if (!isBlank(orderId))
                base += " Numero ordine: " + orderId + ".";
            return base + " Vuoi aggiungere altro o passo a ritiro/consegna?";
        } catch (Exception e) {
            return "Ordine ricevuto. Vuoi aggiungere altro o passo a ritiro/consegna?";
        }
    }

    private static BigDecimal parseQty(String s) {
        if (s == null)
            return null;
        try {
            return new BigDecimal(s.replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null)
            return null;
        for (String v : vals)
            if (v != null && !v.isBlank())
                return v;
        return null;
    }
}
