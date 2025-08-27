package it.overzoom.ordinainchat.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import it.overzoom.ordinainchat.model.Conversation;
import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.model.User;
import it.overzoom.ordinainchat.service.ChatFlowService;
import it.overzoom.ordinainchat.service.ChatHistoryService;
import it.overzoom.ordinainchat.service.OpenAiService;
import it.overzoom.ordinainchat.service.OpenAiService.ChatMessage;
import it.overzoom.ordinainchat.service.PromptLoader;
import it.overzoom.ordinainchat.service.UserService;
import it.overzoom.ordinainchat.util.TextUtils;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private final OpenAiService openAiService;
    private final UserService userService;
    private final PromptLoader promptLoader;
    private final ChatHistoryService chatHistoryService;
    private final ChatFlowService chatFlow;

    public TelegramWebhookController(OpenAiService openAiService,
            UserService userService,
            ChatHistoryService chatHistoryService,
            PromptLoader promptLoader,
            ChatFlowService chatFlow) {
        this.openAiService = openAiService;
        this.userService = userService;
        this.chatHistoryService = chatHistoryService;
        this.promptLoader = promptLoader;
        this.chatFlow = chatFlow;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> onUpdate(@RequestBody Map<String, Object> update) {
        log.info("Received Telegram update: {}", update);
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        String text = (String) message.get("text");
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        Map<String, Object> from = (Map<String, Object>) message.get("from");
        String chatType = String.valueOf(chat.get("type")); // "private", "group", ...
        String telegramUserId = String.valueOf(from.get("id"));
        log.info("TG ids: chat.id={}, from.id={}, type={}", chat.get("id"), from.get("id"), chatType);
        // Per private chat usiamo SEMPRE from.id come chiave
        String chatKey = "private".equals(chatType)
                ? String.valueOf(from.get("id"))
                : String.valueOf(chat.get("id"));

        User user = userService.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> userService.createWithTelegramId(telegramUserId));
        Conversation conv = chatHistoryService.ensureConversation(user.getId(), chatKey);
        chatHistoryService.append(conv.getId(), Message.Role.USER, text, null, null);

        // 1) Se NON abbiamo ancora una conversation OpenAI -> creala e bootstrap init
        if (conv.getOpenAiConversationId() == null || conv.getOpenAiConversationId().isBlank()) {
            String convId = openAiService.createConversation("OrdinaInChat - " + telegramUserId);
            String initSystem = promptLoader.loadInitSystemPrompt(user.getId());
            openAiService.bootstrapConversation(convId, initSystem);
            conv.setOpenAiConversationId(convId);
            chatHistoryService.save(conv);
        }

        // 2) Prepara i messaggi del turno (system dinamico + user)
        List<Message> context = chatHistoryService.lastMessages(conv.getId(), 5);
        // se vuoi uno snippet system per lo stato corrente
        String dynamicSystem = promptLoader.loadDynamicSystemSnippet(user.getCurrentStep(), text, context);

        List<ChatMessage> turn = new ArrayList<>();
        if (dynamicSystem != null && !dynamicSystem.isBlank()) {
            turn.add(new ChatMessage("system", dynamicSystem));
        }
        turn.add(new ChatMessage("user", text));

        // 3) Chiamata Responses API nella conversation
        String raw = openAiService.askInConversation(conv.getOpenAiConversationId(), turn, true);

        // 4) Passa al flow
        String rispostaFinale = chatFlow.handle(
                text, raw, chatKey, telegramUserId, context, guessed -> {
                });

        String finalOut = rispostaFinale;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode bridge = om.readTree(rispostaFinale);
            if (bridge.hasNonNull("bridge_type") && "tool_result".equals(bridge.get("bridge_type").asText())) {
                String tool = bridge.path("tool").asText("");
                com.fasterxml.jackson.databind.JsonNode args = bridge.path("arguments");
                com.fasterxml.jackson.databind.JsonNode result = bridge.path("result");

                // 🔁 items corretti: result.data.items
                com.fasterxml.jackson.databind.JsonNode data = result.path("data");
                com.fasterxml.jackson.databind.node.ArrayNode items = (com.fasterxml.jackson.databind.node.ArrayNode) data
                        .path("items");

                // Compact payload per il secondo giro
                com.fasterxml.jackson.databind.node.ArrayNode compact = om.createArrayNode();
                if (items != null) {
                    for (com.fasterxml.jackson.databind.JsonNode it : items) {
                        com.fasterxml.jackson.databind.node.ObjectNode n = om.createObjectNode();
                        n.put("id", it.path("id").asLong());
                        n.put("name", it.path("name").asText(""));
                        if (it.hasNonNull("priceEur"))
                            n.put("priceEur", it.get("priceEur").asText());
                        if (it.hasNonNull("freshness"))
                            n.put("freshness", it.get("freshness").asText());
                        if (it.hasNonNull("catchDate"))
                            n.put("catchDate", it.get("catchDate").asText());
                        if (it.hasNonNull("source"))
                            n.put("source", it.get("source").asText());
                        compact.add(n);
                    }
                }
                com.fasterxml.jackson.databind.node.ObjectNode toolNode = om.createObjectNode();
                toolNode.put("tool", tool);
                toolNode.set("arguments", args);
                toolNode.set("items", compact);
                String toolSummary = toolNode.toString();

                // Secondo giro nella stessa conversation
                List<OpenAiService.ChatMessage> followup = new ArrayList<>();
                followup.add(new OpenAiService.ChatMessage(
                        "system",
                        "RISULTATO_TOOL: " + toolSummary + "\n" +
                                "Istruzione: usa questi dati per rispondere alla domanda dell’utente in modo conciso. "
                                +
                                "Se la domanda era sulla freschezza/‘di oggi’, rispondi direttamente (FRESH/FROZEN; ‘di oggi’ se catchDate=oggi, tz Europe/Rome). "
                                +
                                "Niente elenco completo a meno che l’utente lo chieda."));
                // 👉 ribadisco la domanda originale
                followup.add(new OpenAiService.ChatMessage("user", text));

                String raw2 = openAiService.askInConversation(conv.getOpenAiConversationId(), followup, true);
                finalOut = raw2; // usa questo come risposta definitiva
            }
        } catch (Exception ignore) {
        }

        // ✅ salva la risposta effettiva
        chatHistoryService.append(
                conv.getId(),
                Message.Role.ASSISTANT,
                finalOut,
                System.getenv("OPENAI_MODEL"),
                null);

        // ✅ invia quella all’utente
        sendMessageToTelegram(chatKey, TextUtils.toPlainText(finalOut));

        // ✅ ritorna quella
        return ResponseEntity.ok(finalOut);
    }

    private void sendMessageToTelegram(String chatId, String messaggio) {
        String url = "https://api.telegram.org/bot" + System.getenv("TELEGRAM_BOT_TOKEN") + "/sendMessage";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", messaggio);
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }
}