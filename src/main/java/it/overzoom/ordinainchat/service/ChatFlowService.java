package it.overzoom.ordinainchat.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.util.JsonUtils;
import it.overzoom.ordinainchat.util.TextUtils;

@Service
public class ChatFlowService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatFlowService.class);

    private final ObjectMapper om = new ObjectMapper();
    private final IntentService intents;
    private final RenderService render;
    private final ProductService products;
    private final OrderService orders;
    private final McpClient mcp;
    private final CartService cartService;

    public ChatFlowService(IntentService intents, RenderService render, ProductService products, OrderService orders,
            McpClient mcp, CartService cartService) {
        this.intents = intents;
        this.render = render;
        this.products = products;
        this.orders = orders;
        this.mcp = mcp;
        this.cartService = cartService;
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
                        log.info("products_search with args: " + modelArgs);
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
                        String bridgeJson = bridge.toString();
                        log.info("Bridge JSON: {}", bridgeJson);
                        return bridgeJson;
                    }
                    case "ask_quantity" -> {
                        long productId = modelArgs.path("productId").asLong();
                        JsonNode p = products.getById(productId, telegramUserId);

                        if (p != null) {
                            String name = JsonUtils.safeTxt(p, "name");
                            if (p.hasNonNull("pricePiece")
                                    && (p.path("priceKg").isMissingNode() || p.path("priceKg").isNull())) {
                                return "Quanti pezzi di " + name + " desideri ordinare?";
                            } else {
                                return "Quanti kg di " + name + " desideri ordinare?";
                            }
                        }
                        return "Per questo prodotto vuoi indicare i kg o i pezzi?";
                    }
                    case "orders_create" -> {
                        // intercetto e trasformo in cart_add
                        ObjectNode ocArgs = orders.sanitizeOrdersCreateArgs((ObjectNode) modelArgs, telegramUserId,
                                null);

                        // mappa → args per cart_add (normalizzando quantity)
                        ObjectNode cartArgs = om.createObjectNode();
                        ArrayNode arr = om.createArrayNode();
                        for (JsonNode it : ocArgs.withArray("items")) {
                            ObjectNode n = om.createObjectNode();
                            n.put("productId", it.path("productId").asLong());
                            // prendi quantityKg se presente, altrimenti quantity
                            double q = it.hasNonNull("quantityKg") ? it.get("quantityKg").asDouble()
                                    : it.path("quantity").asDouble(0d);
                            n.put("quantity", q);
                            if (it.hasNonNull("deliveryDate"))
                                n.put("deliveryDate", it.get("deliveryDate").asText());
                            arr.add(n);
                        }
                        cartArgs.set("items", arr);

                        // sanitizza secondo regole del carrello (gestisce default deliveryDate)
                        ObjectNode safeArgs = cartService.sanitizeCartAddArgs(cartArgs, telegramUserId);

                        if (safeArgs.path("items").isArray() && safeArgs.path("items").size() > 0) {
                            cartService.addItemsToCart(safeArgs, telegramUserId);
                            return "Ho aggiunto gli articoli al carrello. Vuoi confermare l’ordine?";
                        }
                        return "Ok. Per quale articolo e quanti kg?";
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
                        String detail = render.productDetail(body);
                        return detail;
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
                    case "cart_add" -> {
                        ObjectNode rawArgs = (ObjectNode) modelArgs;
                        ObjectNode safeArgs = cartService.sanitizeCartAddArgs(rawArgs, telegramUserId);
                        if (!safeArgs.path("items").isArray() || safeArgs.path("items").size() == 0)
                            return "Ok. Quali articoli vuoi aggiungere al carrello?";
                        cartService.addItemsToCart(safeArgs, telegramUserId);
                        return "Perfetto. Ti serve qualcos'altro?";
                    }
                    case "cart_view" -> {
                        List<CartService.CartItem> items = cartService.getItems(telegramUserId);
                        if (items.isEmpty()) {
                            return "Il carrello è vuoto.";
                        } else {
                            StringBuilder sb = new StringBuilder("Nel carrello hai:\n");
                            for (CartService.CartItem it : items) {
                                String prodName = products.resolveName(it.productId(), telegramUserId);
                                String unit = (it.priceKg() != null) ? "kg" : "pz";
                                sb.append("- ").append(it.quantity()).append(" ").append(unit).append(" di ")
                                        .append((prodName != null) ? prodName : ("prodotto #" + it.productId()))
                                        .append(" (consegna il ").append(it.deliveryDate()).append(")\n");
                            }
                            return sb.toString();
                        }
                    }
                    case "cart_clear" -> {
                        cartService.clear(telegramUserId);
                        return "Il carrello è stato svuotato.";
                    }
                    case "cart_checkout" -> {
                        List<CartService.CartItem> items = cartService.getItems(telegramUserId);
                        if (items.isEmpty()) {
                            return "Il carrello è vuoto, non posso creare l’ordine.";
                        }

                        // Costruisci l'arguments per orders_create
                        ArrayNode arr = om.createArrayNode();
                        for (CartService.CartItem it : items) {
                            ObjectNode n = om.createObjectNode();
                            n.put("productId", it.productId());
                            n.put("quantity", it.quantity());
                            n.put("deliveryDate", it.deliveryDate().toString());
                            arr.add(n);
                        }

                        ObjectNode args = om.createObjectNode();
                        args.set("items", arr);
                        args.put("note", "Ordine via Telegram");
                        args.put("inSite", true);
                        args.put("bookedSlot", items.get(0).deliveryDate().toString()); // semplificato

                        ObjectNode meta = om.createObjectNode();
                        meta.put("telegramUserId", telegramUserId);

                        ObjectNode payload = om.createObjectNode();
                        payload.put("tool", "orders_create");
                        payload.set("arguments", args);
                        payload.set("meta", meta);

                        // Chiama MCP server
                        String body = mcp.call(payload);

                        // Pulisci carrello
                        cartService.clear(telegramUserId);

                        return orders.renderOrderConfirmation(body, args, telegramUserId);
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

        // Aggiungere solo il filtro availableOnly se specificato
        if (JsonUtils.boolOr(modelNode, "availableOnly", false)) {
            args.put("availableOnly", true);
        }

        // Non aggiungere "freshness" se non richiesto esplicitamente
        String frRaw = JsonUtils.textOrNull(modelNode, "freshness");
        if (frRaw != null) {
            String f = frRaw.trim().toUpperCase(java.util.Locale.ITALY);
            if ("FRESH".equals(f) || "FROZEN".equals(f))
                args.put("freshness", f);
        }

        // Resto della logica di gestione dei filtri
        BigDecimal maxPrice = JsonUtils.decimalOrNull(modelNode, "maxPrice");
        if (maxPrice != null)
            args.put("maxPrice", maxPrice);

        int limit = Math.max(1, Math.min(JsonUtils.intOr(modelNode, "limit", 10), 50));
        args.put("page", 0);
        args.put("size", limit);

        // (Facoltativo) filtri locali derivati dal testo utente
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
