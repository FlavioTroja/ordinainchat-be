package it.overzoom.ordinainchat.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.overzoom.ordinainchat.dto.FindOffersAction;
import it.overzoom.ordinainchat.model.Conversation;
import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.model.Product;
import it.overzoom.ordinainchat.model.User;
import it.overzoom.ordinainchat.repository.ProductRepository;
import it.overzoom.ordinainchat.search.ProductSearchCriteria;
import it.overzoom.ordinainchat.search.ProductSearchMapper;
import it.overzoom.ordinainchat.search.ProductSearchRequest;
import it.overzoom.ordinainchat.service.ChatHistoryService;
import it.overzoom.ordinainchat.service.OpenAiService;
import it.overzoom.ordinainchat.service.OpenAiService.ChatMessage;
import it.overzoom.ordinainchat.service.PromptLoader;
import it.overzoom.ordinainchat.service.UserService;
import it.overzoom.ordinainchat.type.SortType;

@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private final ProductRepository productRepository;
    private final OpenAiService openAiService;
    private final UserService userService;
    private final PromptLoader promptLoader;
    private final ChatHistoryService chatHistoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramWebhookController(OpenAiService openAiService, UserService userService,
            ChatHistoryService chatHistoryService, PromptLoader promptLoader, ProductRepository productRepository) {
        this.openAiService = openAiService;
        this.userService = userService;
        this.chatHistoryService = chatHistoryService;
        this.promptLoader = promptLoader;
        this.productRepository = productRepository;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> onUpdate(@RequestBody Map<String, Object> update) {
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        String text = (String) message.get("text");
        String chatId = String.valueOf(((Map<String, Object>) message.get("chat")).get("id"));
        String telegramUserId = String.valueOf(((Map<String, Object>) message.get("from")).get("id"));

        // 1) upsert User per telegramUserId
        User user = userService.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> userService.createWithTelegramId(telegramUserId));

        // 2) ensure Conversation
        Conversation conv = chatHistoryService.ensureConversation(user.getId(), chatId);

        // 3) append messaggio utente in history
        chatHistoryService.append(conv.getId(), Message.Role.USER, text, null, null);

        // 4) recupera contesto (ultimi N) già ordinato dal più recente al meno recente?
        // Uniformiamo.
        List<Message> context = chatHistoryService.lastMessages(conv.getId(), 12);

        String systemPrompt = promptLoader.loadSystemPrompt(user.getId());

        // 6) costruiamo i messaggi per OpenAI: system + history in ordine cronologico
        List<ChatMessage> chatMessages = buildMessages(systemPrompt, context);

        // 6) chiamiamo OpenAI
        String raw = openAiService.askChatGpt(chatMessages);

        String rispostaFinale;
        try {
            // prova a capire se è un JSON di “azione”
            var node = objectMapper.readTree(raw.trim());
            if (node.hasNonNull("action")) {
                String action = node.get("action").asText("");
                switch (action.toUpperCase()) {
                    case "FIND_OFFERS" -> {
                        // Deserializza nei campi tipizzati
                        FindOffersAction cmd = objectMapper.treeToValue(node, FindOffersAction.class);

                        // Costruisci la ProductSearchRequest riusando i campi dell’azione
                        ProductSearchRequest req = new ProductSearchRequest();
                        req.setSearch(emptyToNull(cmd.query()));
                        req.setMaxPrice(cmd.maxPrice());
                        req.setOnlyOnOffer(cmd.onlyOnOffer());
                        req.setFreshFromDate(emptyToNull(cmd.freshFromDate()));
                        req.setItems(cmd.items());
                        req.setIncludePrepared(cmd.includePrepared());

                        // sort opzionale (stringa → enum)
                        if (cmd.sort() != null && !cmd.sort().isBlank()) {
                            try {
                                req.setSortType(SortType.valueOf(cmd.sort()));
                            } catch (Exception ignore) {
                            }
                        }

                        // request → criteria
                        var criteria = ProductSearchMapper.toCriteria(req);

                        int limit = (cmd.limit() != null && cmd.limit() > 0) ? cmd.limit() : 10;
                        Page<Product> page = productRepository.search(criteria, PageRequest.of(0, limit));

                        if (page.isEmpty()) {
                            rispostaFinale = "Al momento non risultano prodotti in promozione.";
                        } else {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Ecco le offerte disponibili");
                            if (req.getSearch() != null)
                                sb.append(" per \"").append(req.getSearch()).append("\"");
                            sb.append(":\n");
                            page.getContent().forEach(p -> {
                                sb.append("• ").append(p.getName());
                                if (p.getDescription() != null && !p.getDescription().isBlank()) {
                                    sb.append(" — ").append(p.getDescription());
                                }
                                if (p.getPrice() != null) {
                                    sb.append(" — € ")
                                            .append(String.format(java.util.Locale.ITALY, "%.2f", p.getPrice()))
                                            .append("/kg");
                                }
                                sb.append("\n");
                            });
                            rispostaFinale = sb.toString().trim();
                        }
                    }
                    // …altri case (FIND_PRICE, FIND_FRESHNESS, ecc.) restano uguali
                    default -> rispostaFinale = raw; // non è un’azione gestita → testo libero
                }
            } else {
                rispostaFinale = raw; // non è JSON d’azione → testo libero
            }
        } catch (Exception e) {
            // non è JSON o parsing fallito → testo libero
            rispostaFinale = raw;
        }

        // append ASSISTANT + send
        chatHistoryService.append(conv.getId(), Message.Role.ASSISTANT, rispostaFinale, System.getenv("OPENAI_MODEL"),
                null);
        sendMessageToTelegram(chatId, rispostaFinale);
        return ResponseEntity.ok("OK");

    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
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

    /**
     * Cerca il prodotto più rilevante per nome/descrizione usando il tuo repository
     * custom.
     * - match case-insensitive su name/description
     * - ritorna il primo risultato ordinato per nome asc (fallback).
     */
    public Product findBestProduct(String nameOrQuery) {
        if (nameOrQuery == null || nameOrQuery.isBlank())
            return null;

        ProductSearchCriteria c = new ProductSearchCriteria();
        c.setSearch(nameOrQuery);
        c.setSortType(SortType.NAME_ASC);

        Page<Product> page = productRepository.search(c, PageRequest.of(0, 1));
        if (page.isEmpty())
            return null;
        return page.getContent().get(0);
    }
}
