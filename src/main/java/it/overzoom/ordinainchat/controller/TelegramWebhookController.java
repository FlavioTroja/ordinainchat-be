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

        chatHistoryService.append(conv.getId(), Message.Role.ASSISTANT, rispostaFinale,
                System.getenv("OPENAI_MODEL"), null);
        sendMessageToTelegram(chatKey, TextUtils.toPlainText(rispostaFinale));
        return ResponseEntity.ok(rispostaFinale);
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