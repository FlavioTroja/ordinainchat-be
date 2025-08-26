package it.overzoom.ordinainchat.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        String chatId = String.valueOf(((Map<String, Object>) message.get("chat")).get("id"));
        String telegramUserId = String.valueOf(((Map<String, Object>) message.get("from")).get("id"));

        User user = userService.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> userService.createWithTelegramId(telegramUserId));
        Conversation conv = chatHistoryService.ensureConversation(user.getId(), chatId);
        // chatHistoryService.append(conv.getId(), Message.Role.USER, text, null, null);

        List<Message> context = chatHistoryService.lastMessages(conv.getId(), 5);
        String systemPrompt = promptLoader.loadSystemPrompt(user.getId());
        List<ChatMessage> chatMessages = buildMessages(systemPrompt, context, text);
        String raw = openAiService.askChatGpt(chatMessages);

        String rispostaFinale = chatFlow.handle(
                text,
                raw,
                chatId,
                telegramUserId,
                context,
                guessed -> {
                    /* pendingProductByChat.put(chatId, guessed); se vuoi mantenerlo qui */ });

        chatHistoryService.append(conv.getId(), Message.Role.ASSISTANT, rispostaFinale,
                System.getenv("OPENAI_MODEL"), null);
        // sendMessageToTelegram(chatId, TextUtils.toPlainText(rispostaFinale));
        return ResponseEntity.ok(rispostaFinale);
    }

    private List<OpenAiService.ChatMessage> buildMessages(String system, List<Message> ctx, String text) {
        List<Message> ordered = new ArrayList<>(ctx);
        ordered.sort(Comparator.comparing(Message::getCreatedAt));
        List<OpenAiService.ChatMessage> out = new ArrayList<>();
        out.add(new OpenAiService.ChatMessage("system", system));
        if (!ordered.isEmpty())
            out.add(new OpenAiService.ChatMessage("system",
                    "Basa la risposta all'utente sulla seguente cronologia ordinata in modo crescente di messaggi:"
                            + ordered.stream().map(m -> {
                                return (m.getRole() == Message.Role.ASSISTANT) ? "assistant: "
                                        : "user: " + m.getContent();
                            }).collect(Collectors.joining("\n"))));
        // for (Message m : ordered) {
        // String role = (m.getRole() == Message.Role.ASSISTANT) ? "assistant" : "user";
        // out.add(new OpenAiService.ChatMessage(role, m.getContent()));
        // }
        out.add(new OpenAiService.ChatMessage("user", text));
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
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }
}