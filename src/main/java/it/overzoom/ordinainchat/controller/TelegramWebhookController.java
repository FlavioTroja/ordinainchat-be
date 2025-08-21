package it.overzoom.ordinainchat.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.overzoom.ordinainchat.model.Conversation;
import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.model.User;
import it.overzoom.ordinainchat.service.ChatHistoryService;
import it.overzoom.ordinainchat.service.OpenAiService;
import it.overzoom.ordinainchat.service.OpenAiService.ChatMessage;
import it.overzoom.ordinainchat.service.PromptLoader;
import it.overzoom.ordinainchat.service.UserService;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    @org.springframework.beans.factory.annotation.Value("${mcp.server.base-url:http://localhost:5000/api/mcp}")
    private String mcpBaseUrl;

    @org.springframework.beans.factory.annotation.Value("${mcp.api-key:}")
    private String mcpApiKey;

    private final OpenAiService openAiService;
    private final UserService userService;
    private final PromptLoader promptLoader;
    private final ChatHistoryService chatHistoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramWebhookController(OpenAiService openAiService, UserService userService,
            ChatHistoryService chatHistoryService, PromptLoader promptLoader) {
        this.openAiService = openAiService;
        this.userService = userService;
        this.chatHistoryService = chatHistoryService;
        this.promptLoader = promptLoader;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> onUpdate(@RequestBody Map<String, Object> update) {
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        String text = (String) message.get("text");
        String chatId = String.valueOf(((Map<String, Object>) message.get("chat")).get("id"));
        String telegramUserId = String.valueOf(((Map<String, Object>) message.get("from")).get("id"));

        // 1) upsert User
        User user = userService.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> userService.createWithTelegramId(telegramUserId));

        // 2) ensure Conversation
        Conversation conv = chatHistoryService.ensureConversation(user.getId(), chatId);

        // 3) save USER msg
        chatHistoryService.append(conv.getId(), Message.Role.USER, text, null, null);

        // 4) context
        List<Message> context = chatHistoryService.lastMessages(conv.getId(), 12);
        String systemPrompt = promptLoader.loadSystemPrompt(user.getId());

        // 5) LLM
        List<ChatMessage> chatMessages = buildMessages(systemPrompt, context);
        String raw = openAiService.askChatGpt(chatMessages);

        String rispostaFinale;
        try {
            // proviamo a leggere un JSON azione in stile MCP
            JsonNode node = safeParseAction(raw);
            if (node != null && node.hasNonNull("tool")) {
                String tool = node.get("tool").asText("");
                JsonNode modelArgs = node.path("arguments");
                switch (tool.toLowerCase(java.util.Locale.ITALY)) {
                    case "greeting", "hello", "hi" -> {
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

                        String mcpBody = callMcp(payload);
                        rispostaFinale = renderProductsSearchReply(mcpBody);
                    }
                    default -> {
                        rispostaFinale = "Ciao! Posso aiutarti con prodotti, offerte, prezzi o creare un ordine. Dimmi pure.";
                    }
                }
            } else {
                // non è JSON MCP → testo libero
                rispostaFinale = raw;
            }
        } catch (Exception e) {
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

    private String callMcp(JsonNode payload) {
        String url = mcpBaseUrl + "/call";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (mcpApiKey != null && !mcpApiKey.isBlank()) {
            headers.add("X-MCP-KEY", mcpApiKey);
        }
        HttpEntity<String> entity = new HttpEntity<>(payload.toString(), headers);
        try {
            RestTemplate rt = new RestTemplate();
            ResponseEntity<String> res = rt.postForEntity(url, entity, String.class);
            return res.getBody();
        } catch (HttpStatusCodeException ex) {
            return """
                    {"status":"error","message":"client_error: %s","httpStatus":%d,"body":%s}
                    """.formatted(ex.getStatusText(), ex.getStatusCode().value(),
                    jsonSafe(ex.getResponseBodyAsString()));
        } catch (Exception e) {
            return """
                    {"status":"error","message":"client_error: %s"}
                    """.formatted(jsonSafe(e.getMessage()));
        }
    }

    private String jsonSafe(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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

}
