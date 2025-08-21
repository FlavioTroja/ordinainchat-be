package it.overzoom.ordinainchat.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.overzoom.ordinainchat.model.Conversation;
import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.model.User;
import it.overzoom.ordinainchat.service.ChatHelperService;
import it.overzoom.ordinainchat.service.ChatHistoryService;
import it.overzoom.ordinainchat.service.McpService;
import it.overzoom.ordinainchat.service.OpenAiService;
import it.overzoom.ordinainchat.service.OpenAiService.ChatMessage;
import it.overzoom.ordinainchat.service.PromptLoader;
import it.overzoom.ordinainchat.service.UserService;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private final Logger logger = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final OpenAiService openAiService;
    private final UserService userService;
    private final PromptLoader promptLoader;
    private final ChatHistoryService chatHistoryService;
    private final ChatHelperService chatHelperService;
    private final McpService mcpService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramWebhookController(OpenAiService openAiService, UserService userService,
            ChatHistoryService chatHistoryService, PromptLoader promptLoader, ChatHelperService chatHelperService,
            McpService mcpService) {
        this.openAiService = openAiService;
        this.userService = userService;
        this.chatHistoryService = chatHistoryService;
        this.promptLoader = promptLoader;
        this.chatHelperService = chatHelperService;
        this.mcpService = mcpService;
    }

    private final Map<String, String> pendingProductByChat = new ConcurrentHashMap<>();

    @PostMapping("/webhook")
    public ResponseEntity<String> onUpdate(@RequestBody Map<String, Object> update) {
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        String text = (String) message.get("text");
        String chatId = String.valueOf(((Map<String, Object>) message.get("chat")).get("id"));
        String telegramUserId = String.valueOf(((Map<String, Object>) message.get("from")).get("id"));

        // 1) upsert User
        logger.info("Received message from Telegram user {}: {}", telegramUserId, text);
        User user = userService.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> userService.createWithTelegramId(telegramUserId));

        // 2) ensure Conversation
        logger.info("Ensuring conversation for chatId: {}, telegramUserId: {}, user: {}", chatId, telegramUserId, user);
        Conversation conv = chatHistoryService.ensureConversation(user.getId(), chatId);

        // 3) save USER msg
        logger.info("Saving user message for telegramUserId: {}, user: {}, conv: {}", telegramUserId, user, conv);
        chatHistoryService.append(conv.getId(), Message.Role.USER, text, null, null);

        // 4) context
        logger.info("Building context for telegramUserId: {}", telegramUserId);
        List<Message> context = chatHistoryService.lastMessages(conv.getId(), 12);
        String systemPrompt = promptLoader.loadSystemPrompt(user.getId());

        // 5) LLM
        List<ChatMessage> chatMessages = buildMessages(systemPrompt, context);
        String raw = openAiService.askChatGpt(chatMessages);

        String rispostaFinale;
        try {
            if (raw != null && raw.toLowerCase(Locale.ITALY).contains("quanti kg")) {
                String guessed = chatHelperService.guessProductNameFromText(raw);
                if (guessed != null) {
                    pendingProductByChat.put(chatId, guessed);
                }
            }
            // proviamo a leggere un JSON azione in stile MCP
            JsonNode node = safeParseAction(raw);
            logger.info("Raw response: {}, Parsed node: {}", raw, node);
            if (node != null && node.hasNonNull("tool")) {
                String tool = node.get("tool").asText("");
                JsonNode modelArgs = node.path("arguments");
                switch (tool.toLowerCase(Locale.ITALY)) {
                    case "greeting", "hello", "hi" -> {
                        logger.info("Responding to greeting for telegramUserId: {}", telegramUserId);
                        rispostaFinale = "Ciao! 👋 Posso dirti cosa c’è di fresco o in offerta, i prezzi al kg, oppure creare un ordine.";
                    }
                    case "help" -> {
                        rispostaFinale = "Puoi chiedermi, ad esempio:\n• Cosa hai di fresco?\n• Cosa hai in offerta oggi?\n• A quanto vanno le triglie?\n• Le spigole sono surgelate?\n• Vorrei 1,5 kg di cozze per stasera.";
                    }
                    case "products_search" -> {
                        ObjectNode args = sanitizeProductsSearchArgs(modelArgs, text);
                        ObjectNode meta = objectMapper.createObjectNode();
                        meta.put("telegramUserId", String.valueOf(telegramUserId));
                        ObjectNode payload = objectMapper.createObjectNode();
                        payload.put("tool", "products_search");
                        payload.set("arguments", args);
                        payload.set("meta", meta);

                        logger.info("Calling MCP for products search with payload: {}", payload);
                        String mcpBody = mcpService.callMcp(payload);
                        logger.info("MCP response: {}", mcpBody);
                        rispostaFinale = renderProductsSearchReply(mcpBody);
                        logger.info("Final response for products search: {}", rispostaFinale);
                    }
                    default -> {
                        rispostaFinale = "Ciao! Posso aiutarti con prodotti, offerte, prezzi o creare un ordine. Dimmi pure.";
                    }
                }
            } else {
                logger.info("Non-JSON MCP response for telegramUserId: {}", telegramUserId);
                String lower = (text == null ? "" : text.toLowerCase(Locale.ITALY));

                // 1) Se l’utente ha risposto con un numero → prova a trattarlo come kg
                if (isNumericQuantity(lower)) {
                    String qtyStr = lower.replace(",", ".").trim();
                    String handled = maybeHandleQuantityReply(chatId, telegramUserId, qtyStr, context);
                    if (handled != null) {
                        rispostaFinale = handled;
                    } else {
                        // Non sono riuscito a capire il prodotto → chiedi quale articolo
                        rispostaFinale = "Ok, " + qtyStr + " kg. Per quale articolo li desideri?";
                    }
                } else {
                    // 2) Altrimenti, prova intent impliciti (fresco/offerte/locale)
                    String forced = maybeHandleImplicitIntents(lower, telegramUserId);
                    if (forced != null) {
                        rispostaFinale = forced;
                    } else {
                        // 3) Niente di speciale: mostra il testo così com’è
                        rispostaFinale = raw;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing message for telegramUserId: {}", telegramUserId, e);
            rispostaFinale = raw;
        }

        // 8) save ASSISTANT + send
        chatHistoryService.append(conv.getId(), Message.Role.ASSISTANT, rispostaFinale, System.getenv("OPENAI_MODEL"),
                null);
        sendMessageToTelegram(chatId, rispostaFinale);
        return ResponseEntity.ok("OK");
    }

    private List<ChatMessage> buildMessages(String system, List<Message> ctx) {
        // Riordino dal più vecchio al più recente
        List<Message> ordered = new ArrayList<>(ctx);
        ordered.sort(Comparator.comparing(Message::getCreatedAt));

        List<ChatMessage> out = new ArrayList<>();
        out.add(new ChatMessage("system", system));

        for (Message m : ordered) {
            String role = switch (m.getRole()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                default -> "user";
            };
            out.add(new ChatMessage(role, m.getContent()));
        }
        return out;
    }

    private void sendMessageToTelegram(String chatId, String messaggio) {
        String url = "https://api.telegram.org/bot" + System.getenv("TELEGRAM_BOT_TOKEN") + "/sendMessage";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", messaggio);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, entity, String.class);
    }

    private ObjectNode sanitizeProductsSearchArgs(JsonNode modelNode,
            String userUtterance) {
        ObjectMapper om = this.objectMapper;
        ObjectNode args = om.createObjectNode();

        String text = textOrNull(modelNode, "query", "text", "textSearch");
        if (text != null && !text.isBlank())
            args.put("textSearch", text.trim());

        // onlyOnOffer
        if (boolOr(modelNode, "onlyOnOffer", false))
            args.put("onlyOnOffer", true);

        // freshness: solo FRESH o FROZEN
        String frRaw = textOrNull(modelNode, "freshness");
        if (frRaw != null) {
            String f = frRaw.trim().toUpperCase(java.util.Locale.ITALY);
            if ("FRESH".equals(f) || "FROZEN".equals(f))
                args.put("freshness", f);
        }

        // maxPrice numerico valido
        java.math.BigDecimal maxPrice = decimalOrNull(modelNode, "maxPrice");
        if (maxPrice != null)
            args.put("maxPrice", maxPrice);

        // limit -> size (clampa 1..50); page=0
        int limit = intOr(modelNode, "limit", 10);
        limit = Math.max(1, Math.min(limit, 50));
        args.put("page", 0);
        args.put("size", limit);

        String u = (userUtterance == null) ? "" : userUtterance.toLowerCase(Locale.ITALY);
        boolean askLocal = u.contains("locale") || u.contains("puglia") || u.contains("pugliese") ||
                u.contains("adriatico") || u.contains("ionio") || u.contains("italia") || u.contains("italiano");

        if (askLocal) {
            args.put("originCountry", "IT");
            args.put("faoAreaPrefix", "37.2"); // Mediterraneo Centrale (Adriatico/Ionio)
            args.put("originAreaLike", "puglia|adriatico|ionio");
            args.put("landingPortLike", "bari|brindisi|taranto|manfredonia|monopoli|molfetta|trani|barletta|gallipoli");
            // se non fissato dal modello, preferisci fresco e pescato
            if (!args.has("freshness"))
                args.put("freshness", "FRESH");
            args.put("source", "WILD_CAUGHT");
        }

        return args;
    }

    private String textOrNull(JsonNode n, String... keys) {
        for (String k : keys)
            if (n.hasNonNull(k))
                return n.get(k).asText();
        return null;
    }

    private boolean boolOr(JsonNode n, String key, boolean dflt) {
        return n.hasNonNull(key) ? n.get(key).asBoolean(dflt) : dflt;
    }

    private int intOr(JsonNode n, String key, int dflt) {
        return n.hasNonNull(key) ? n.get(key).asInt(dflt) : dflt;
    }

    private java.math.BigDecimal decimalOrNull(JsonNode n, String key) {
        if (!n.hasNonNull(key))
            return null;
        try {
            return new java.math.BigDecimal(n.get(key).asText());
        } catch (Exception e) {
            return null;
        }
    }

    private String renderProductsSearchReply(String mcpResponseBody) {
        try {
            JsonNode root = objectMapper.readTree(mcpResponseBody == null ? "{}" : mcpResponseBody);

            // gestione errori standardizzati dal server
            String status = root.path("status").asText("");
            if ("error".equalsIgnoreCase(status)) {
                String msg = root.path("message").asText("Errore MCP");
                return "Errore dal gestionale: " + msg;
            }

            JsonNode data = root.path("data");
            if (data.isMissingNode()) {
                // alcuni server possono restituire direttamente la lista
                data = root;
            }

            JsonNode items = data.path("items");
            if (!items.isArray() || items.size() == 0) {
                return "Al momento non risultano prodotti in promozione.";
            }

            StringBuilder sb = new StringBuilder("Ecco le offerte disponibili:\n");
            for (int i = 0; i < items.size(); i++) {
                JsonNode p = items.get(i);
                String name = safeTxt(p, "name");
                String desc = safeTxt(p, "description");
                String price = formatPrice(p.path("priceEur"));
                String freshness = safeTxt(p, "freshness"); // FRESH/FROZEN
                String source = safeTxt(p, "source"); // WILD_CAUGHT/FARMED

                List<String> extra = new ArrayList<>();
                String originArea = safeTxt(p, "originArea");
                String faoArea = safeTxt(p, "faoArea");
                String originCountry = safeTxt(p, "originCountry");
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

                // nota freschezza/fonte se presenti
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
            // fallback grezzo
            return "Errore nel parsing della risposta dal gestionale.";
        }
    }

    private String safeTxt(JsonNode n, String key) {
        if (!n.hasNonNull(key))
            return "";
        String v = n.get(key).asText("");
        return v == null ? "" : v.trim();
    }

    private String formatPrice(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull())
            return "";
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(n.asText());
            return String.format(java.util.Locale.ITALY, "€ %.2f", bd);
        } catch (Exception e) {
            return "";
        }
    }

    // dentro TelegramWebhookController

    private JsonNode safeParseAction(String raw) throws Exception {
        if (raw == null)
            return null;
        String s = raw.trim();

        // 1) Se è wrappato in code fence ```...```, estrai il contenuto
        if (s.startsWith("```")) {
            int first = s.indexOf("```");
            int last = s.lastIndexOf("```");
            if (last > first) {
                s = s.substring(first + 3, last).trim();
            }
        }

        // 2) Se non è puro JSON, prova a cercare un blocco che inizi con {"tool":
        int idx = s.indexOf("{\"tool\"");
        if (idx >= 0) {
            // trova la fine dell’oggetto bilanciando le graffe
            int end = findMatchingBraceEnd(s, idx);
            if (end > idx) {
                String jsonSlice = s.substring(idx, end + 1);
                try {
                    return objectMapper.readTree(jsonSlice);
                } catch (Exception ignore) {
                    // continua con tentativi successivi
                }
            }
        }

        // 3) Tentativo “normale”
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            return null; // il chiamante deciderà il fallback
        }
    }

    private int findMatchingBraceEnd(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{')
                depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0)
                    return i;
            }
            // opzionale: gestire stringhe/escape se vuoi essere ultra-rigido
        }
        return -1;
    }

    private String maybeHandleImplicitIntents(String lower, String telegramUserId) {
        // Intent: "fresco oggi", "cosa avete di fresco", ecc.
        boolean askFresh = lower.contains("fresco") || lower.contains("di fresco");
        boolean askOffer = lower.contains("offerta") || lower.contains("in offerta") || lower.contains("promo");
        boolean askLocal = lower.contains("locale") || lower.contains("puglia") || lower.contains("pugliese")
                || lower.contains("adriatico") || lower.contains("ionio") || lower.contains("italia")
                || lower.contains("italiano");

        if (!askFresh && !askOffer && !askLocal) {
            return null; // niente forcing → lascia il flusso attuale
        }

        // Costruisci payload products_search
        ObjectNode args = objectMapper.createObjectNode();
        if (askFresh) {
            args.put("freshness", "FRESH");
        }
        if (askOffer) {
            args.put("onlyOnOffer", true);
        }
        if (askLocal) {
            args.put("originCountry", "IT");
            args.put("faoAreaPrefix", "37.2"); // Adriatico/Ionio
            args.put("originAreaLike", "puglia|adriatico|ionio");
            args.put("landingPortLike", "bari|brindisi|taranto|manfredonia|monopoli|molfetta|trani|barletta|gallipoli");
            if (!args.has("freshness"))
                args.put("freshness", "FRESH");
            args.put("source", "WILD_CAUGHT");
        }
        args.put("page", 0);
        args.put("size", 10);

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("telegramUserId", String.valueOf(telegramUserId));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("tool", "products_search");
        payload.set("arguments", args);
        payload.set("meta", meta);

        logger.info("FORCED MCP call (implicit intent) with payload: {}", payload);
        String mcpBody = mcpService.callMcp(payload);
        logger.info("FORCED MCP response: {}", mcpBody);

        // Heading in base all’intento
        String heading;
        if (askFresh && askOffer)
            heading = "Ecco il fresco in offerta:";
        else if (askFresh)
            heading = "Oggi fresco disponibile:";
        else if (askOffer)
            heading = "Ecco le offerte disponibili:";
        else if (askLocal)
            heading = "Prodotti locali disponibili:";
        else
            heading = "Risultati disponibili:";

        return renderProductsSearchReplyWithHeading(mcpBody, heading);
    }

    private String renderProductsSearchReplyWithHeading(String mcpResponseBody, String heading) {
        try {
            JsonNode root = objectMapper.readTree(mcpResponseBody == null ? "{}" : mcpResponseBody);
            String status = root.path("status").asText("");
            if ("error".equalsIgnoreCase(status)) {
                String msg = root.path("message").asText("Errore MCP");
                return "Errore dal gestionale: " + msg;
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode())
                data = root;
            JsonNode items = data.path("items");
            if (!items.isArray() || items.size() == 0) {
                return "Al momento non risultano articoli disponibili per la ricerca richiesta.";
            }

            StringBuilder sb = new StringBuilder(heading).append("\n");
            for (int i = 0; i < items.size(); i++) {
                JsonNode p = items.get(i);
                String name = safeTxt(p, "name");
                String desc = safeTxt(p, "description");
                String price = formatPrice(p.path("priceEur"));
                String freshness = safeTxt(p, "freshness");
                String source = safeTxt(p, "source");

                List<String> extra = new ArrayList<>();
                String originArea = safeTxt(p, "originArea");
                String faoArea = safeTxt(p, "faoArea");
                String originCountry = safeTxt(p, "originCountry");
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

    private boolean isNumericQuantity(String s) {
        if (s == null)
            return false;
        // accetta "2", "2.0", "2,5"
        return s.matches("\\d+(?:[\\.,]\\d+)?");
    }

    // Prova a dedurre il nome prodotto dal contesto recente
    private String guessProductNameFromHistory(List<Message> context) {
        if (context == null || context.isEmpty())
            return null;

        // 1) Se ho salvato un "pending" esplicito, quello ha priorità
        // (usa chatId se vuoi; qui non ce l’ho: gestisco altrove)

        // 2) Cerca nell’ultimo messaggio dell’assistente una riga tipo "Sì, oggi
        // abbiamo le cozze pelose ..."
        for (int i = context.size() - 1; i >= 0; i--) {
            Message m = context.get(i);
            if (m.getRole() == Message.Role.ASSISTANT) {
                String t = (m.getContent() == null) ? "" : m.getContent().toLowerCase(Locale.ITALY);
                String candidate = chatHelperService.guessProductNameFromText(t);
                if (candidate != null)
                    return candidate;
            }
        }

        // 3) In fallback, guarda l’ultima domanda dell’utente con una parola prodotto
        for (int i = context.size() - 1; i >= 0; i--) {
            Message m = context.get(i);
            if (m.getRole() == Message.Role.USER) {
                String t = (m.getContent() == null) ? "" : m.getContent().toLowerCase(Locale.ITALY);
                String candidate = chatHelperService.guessProductNameFromText(t);
                if (candidate != null)
                    return candidate;
            }
        }

        return null;
    }

    // Gestisce la risposta numerica: trova prodotto, prende id via products_search
    // e crea l’ordine
    private String maybeHandleQuantityReply(String chatId, String telegramUserId, String qtyStr,
            List<Message> context) {
        try {
            // 1) kg come BigDecimal
            java.math.BigDecimal qty = new java.math.BigDecimal(qtyStr);

            // 2) Prodotto: prima pending, poi history
            String productName = pendingProductByChat.get(chatId);
            if (productName == null) {
                productName = guessProductNameFromHistory(context);
            }
            if (productName == null) {
                return null; // non so che prodotto → chiedi quale articolo
            }

            // 3) Cerca l’ID con products_search
            ObjectNode searchArgs = objectMapper.createObjectNode();
            searchArgs.put("textSearch", productName);
            // (opzionale) se spesso è fresco:
            // searchArgs.put("freshness", "FRESH");
            searchArgs.put("page", 0);
            searchArgs.put("size", 1);

            ObjectNode searchMeta = objectMapper.createObjectNode();
            searchMeta.put("telegramUserId", telegramUserId);

            ObjectNode searchPayload = objectMapper.createObjectNode();
            searchPayload.put("tool", "products_search");
            searchPayload.set("arguments", searchArgs);
            searchPayload.set("meta", searchMeta);

            logger.info("Quantity flow: searching productId for '{}'", productName);
            String mcpSearch = mcpService.callMcp(searchPayload);

            JsonNode root = objectMapper.readTree(mcpSearch == null ? "{}" : mcpSearch);
            JsonNode data = root.path("data");
            if (data.isMissingNode())
                data = root;
            JsonNode items = data.path("items");
            if (!items.isArray() || items.size() == 0) {
                // non trovato
                return "Non riesco a trovare \"" + productName + "\" adesso. Vuoi che cerchi un’alternativa?";
            }
            long productId = items.get(0).path("id").asLong(0L);
            if (productId <= 0)
                return "Ho un problema a identificare l’articolo. Riproviamo con il nome completo.";

            // 4) Crea ordine
            ObjectNode orderArgs = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
            ObjectNode item = objectMapper.createObjectNode();
            item.put("productId", productId);
            item.put("quantityKg", qty);
            arr.add(item);
            orderArgs.set("items", arr);

            ObjectNode orderMeta = objectMapper.createObjectNode();
            orderMeta.put("telegramUserId", telegramUserId);

            ObjectNode orderPayload = objectMapper.createObjectNode();
            orderPayload.put("tool", "orders_create");
            orderPayload.set("arguments", orderArgs);
            orderPayload.set("meta", orderMeta);

            logger.info("Quantity flow: creating order for productId={}, qtyKg={}", productId, qty);
            String mcpOrder = mcpService.callMcp(orderPayload);

            // (Facoltativo) puoi parsare mcpOrder per un numero ordine, ecc.
            // Qui rispondo in chiaro, NIENTE markdown (niente **, niente _)
            // Pulisco anche eventuale pending
            pendingProductByChat.remove(chatId);

            String niceQty = qty.stripTrailingZeros().toPlainString().replace('.', ',');
            String cleanName = capitalizeWords(productName);
            return "Perfetto: messi da parte " + niceQty + " kg di " + cleanName
                    + ". Vuoi aggiungere altro o passo al ritiro/consegna?";
        } catch (Exception e) {
            logger.error("Quantity flow error", e);
            return "Ho avuto un problema nel registrare la quantità. Puoi ripetere il prodotto e i kg?";
        }
    }

    // Utility per capitalizzare in modo semplice il nome prodotto
    private String capitalizeWords(String s) {
        if (s == null || s.isBlank())
            return s;
        String[] parts = s.split("\\s+");
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

}
