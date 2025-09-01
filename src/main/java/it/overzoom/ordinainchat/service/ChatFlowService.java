package it.overzoom.ordinainchat.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.util.JsonUtils;
import it.overzoom.ordinainchat.util.TextUtils;

@Service
public class ChatFlowService {
    private final ObjectMapper om = new ObjectMapper();
    private final IntentService intents;
    private final RenderService render;
    private final ProductService products;
    private final OrderService orders;
    private final McpClient mcp;

    public ChatFlowService(IntentService intents, RenderService render, ProductService products, OrderService orders,
            McpClient mcp) {
        this.intents = intents;
        this.render = render;
        this.products = products;
        this.orders = orders;
        this.mcp = mcp;
    }

    public String handle(String text, String raw, String chatId, String telegramUserId,
            List<Message> context, java.util.function.Consumer<String> savePendingProduct) {
        try {
            if (raw != null && raw.toLowerCase(Locale.ITALY).contains("quanti kg")) {
                // puoi usare una funzione esterna per estrarre nome e chiamare
                // savePendingProduct.accept(nome)
            }

            BigDecimal parsedQty = TextUtils.parseQuantityKg(text);
            if (parsedQty != null) {
                // logica legata alla pipeline ordine: qui potresti delegare ad OrderService un
                // metodo “handleQuantityFlow”
                // in questo esempio rimandiamo al controller per non complicare
            }

            JsonNode node = JsonUtils.safeParseAction(om, raw);
            if (node != null && node.hasNonNull("tool")) {
                String tool = node.get("tool").asText("");
                JsonNode modelArgs = node.path("arguments");
                switch (tool.toLowerCase(Locale.ITALY)) {
                    case "greeting", "hello", "hi" -> {
                        // prendi il testo dal JSON (se presente), altrimenti fallback
                        String msg = JsonUtils.textOrNull(modelArgs, "message");
                        return (msg != null && !msg.isBlank())
                                ? msg
                                : "Ciao! 👋 Posso dirti cosa c’è di fresco o in offerta, i prezzi al kg, oppure creare un ordine.";
                    }
                    case "help" -> {
                        return "Puoi chiedermi, ad esempio:\n• Cosa hai di fresco?\n• Cosa hai in offerta oggi?\n• A quanto vanno le triglie?\n• Le spigole sono surgelate?\n• Vorrei 1,5 kg di cozze per stasera.";
                    }
                    case "products_search" -> {
                        ObjectNode args = sanitizeProductsSearchArgs(modelArgs, text);

                        ObjectNode meta = om.createObjectNode().put("telegramUserId", telegramUserId);
                        ObjectNode payload = om.createObjectNode();
                        payload.put("tool", "products_search");
                        payload.set("arguments", args);
                        payload.set("meta", meta);

                        String body = mcp.call(payload);

                        ObjectNode bridge = om.createObjectNode();
                        bridge.put("bridge_type", "tool_result");
                        bridge.put("tool", "products_search");
                        bridge.set("arguments", args);
                        bridge.set("result", om.readTree(body));
                        return bridge.toString();
                    }
                    case "orders_create" -> {
                        ObjectNode rawArgs = (ObjectNode) modelArgs;
                        ObjectNode safeArgs = orders.sanitizeOrdersCreateArgs(rawArgs, telegramUserId, /*
                                                                                                        * candidateFromCtx
                                                                                                        */ null);
                        if (!safeArgs.path("items").isArray() || safeArgs.path("items").size() == 0)
                            return "Ok. Per quale articolo e quanti kg?";
                        ObjectNode meta = om.createObjectNode().put("telegramUserId", telegramUserId);
                        ObjectNode payload = om.createObjectNode();
                        payload.put("tool", "orders_create");
                        payload.set("arguments", safeArgs);
                        payload.set("meta", meta);

                        String body = mcp.call(payload);
                        return orders.renderOrderConfirmation(body, safeArgs, telegramUserId);
                    }
                    case "products_byid", "product_by_id" -> {
                        ObjectNode payload = om.createObjectNode();
                        payload.put("tool", "products_byid");
                        payload.set("arguments", (ObjectNode) modelArgs);

                        ObjectNode metaNode = om.createObjectNode();
                        metaNode.put("telegramUserId", telegramUserId);
                        payload.set("meta", metaNode);

                        String body = mcp.call(payload);
                        try {
                            products.cacheIdNameMap(products.parseIdNameMap(body));
                        } catch (Exception ignored) {
                        }
                        return render.productDetail(body);
                    }
                    case "customers_me" -> {
                        ObjectNode payload = om.createObjectNode();
                        payload.put("tool", "customers_me");
                        payload.set("arguments", (ObjectNode) modelArgs);
                        ObjectNode metaNode = om.createObjectNode();
                        metaNode.put("telegramUserId", telegramUserId);
                        payload.set("meta", metaNode);
                        String body = mcp.call(payload);
                        return render.customerProfile(body);
                    }
                    default -> {
                        return (raw != null && !raw.isBlank()) ? raw
                                : "Dimmi pure come posso aiutarti (offerte, prezzi, disponibilità o ordini).";
                    }
                }
            } else {
                return (raw != null) ? raw
                        : "Dimmi pure come posso aiutarti (offerte, prezzi, disponibilità o ordini).";
            }
        } catch (Exception e) {
            return raw;
        }
    }

    private ObjectNode sanitizeProductsSearchArgs(JsonNode modelNode, String userUtterance) {
        ObjectNode args = om.createObjectNode();

        String text = JsonUtils.textOrNull(modelNode, "query", "text", "textSearch");
        if (text != null && !text.isBlank())
            args.put("textSearch", text.trim());

        if (JsonUtils.boolOr(modelNode, "onlyOnOffer", false))
            args.put("onlyOnOffer", true);

        if (JsonUtils.boolOr(modelNode, "availableOnly", false))
            args.put("availableOnly", true);
        String ffd = JsonUtils.textOrNull(modelNode, "freshFromDate");
        if (ffd != null && ffd.matches("\\d{4}-\\d{2}-\\d{2}"))
            args.put("freshFromDate", ffd);

        String frRaw = JsonUtils.textOrNull(modelNode, "freshness");
        if (frRaw != null) {
            String f = frRaw.trim().toUpperCase(java.util.Locale.ITALY);
            if ("FRESH".equals(f) || "FROZEN".equals(f))
                args.put("freshness", f);
        }

        BigDecimal maxPrice = JsonUtils.decimalOrNull(modelNode, "maxPrice");
        if (maxPrice != null)
            args.put("maxPrice", maxPrice);

        int limit = Math.max(1, Math.min(JsonUtils.intOr(modelNode, "limit", 10), 50));
        args.put("page", 0);
        args.put("size", limit);

        // (Facoltativo) filtri “local” derivati dal testo utente: lascia pure, non
        // interferiscono.
        String u = (userUtterance == null) ? "" : userUtterance.toLowerCase(java.util.Locale.ITALY);
        boolean askLocal = u.contains("locale") || u.contains("puglia") || u.contains("pugliese")
                || u.contains("adriatico") || u.contains("ionio") || u.contains("italia") || u.contains("italiano");
        if (askLocal) {
            args.put("originCountry", "IT");
            args.put("faoAreaPrefix", "37.2");
            args.put("originAreaLike", "puglia|adriatico|ionio");
            args.put("landingPortLike", "bari|brindisi|taranto|manfredonia|monopoli|molfetta|trani|barletta|gallipoli");
            if (!args.has("freshness"))
                args.put("freshness", "FRESH");
            args.put("source", "WILD_CAUGHT");
        }
        return args;
    }

}
