package it.overzoom.ordinainchat.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

import com.fasterxml.jackson.databind.JsonNode;
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
import it.overzoom.ordinainchat.type.FreshnessType;
import it.overzoom.ordinainchat.type.SortType;
import it.overzoom.ordinainchat.type.SourceType;

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
            JsonNode node = objectMapper.readTree(raw.trim());
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
                        ProductSearchCriteria criteria = ProductSearchMapper.toCriteria(req);

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
                    case "FIND_FRESHNESS" -> {
                        String productName = node.path("product").asText("").trim();
                        Product p = findBestProduct(productName);

                        if (p == null) {
                            rispostaFinale = "Non ho trovato \"" + productName + "\".";
                        } else {
                            rispostaFinale = formatFreshnessInfo(p);
                        }
                    }
                    case "FIND_FRESH_TODAY" -> {
                        int limit = Math.max(1, node.path("limit").asInt(10));

                        ProductSearchCriteria criteria = new ProductSearchCriteria();
                        criteria.setFreshness(FreshnessType.FRESH); // solo fresco
                        criteria.setFreshFromDate(LocalDate.now()); // oggi in poi

                        Page<Product> page = productRepository.search(criteria, PageRequest.of(0, limit));

                        if (page.isEmpty()) {
                            rispostaFinale = "Oggi non ho pesce fresco disponibile.";
                        } else {
                            StringBuilder sb = new StringBuilder("Oggi fresco disponibile:\n");
                            for (Product p : page.getContent()) {
                                sb.append("• ").append(p.getName());

                                var provenienze = new ArrayList<String>();
                                if (p.getOriginArea() != null && !p.getOriginArea().isBlank())
                                    provenienze.add(p.getOriginArea());
                                if (p.getFaoArea() != null && !p.getFaoArea().isBlank())
                                    provenienze.add("FAO " + p.getFaoArea());
                                if (p.getOriginCountry() != null && !p.getOriginCountry().isBlank())
                                    provenienze.add(p.getOriginCountry());
                                if (!provenienze.isEmpty())
                                    sb.append(" — ").append(String.join(", ", provenienze));

                                if (p.getPrice() != null) {
                                    sb.append(" — € ").append(String.format(Locale.ITALY, "%.2f", p.getPrice()))
                                            .append("/kg");
                                }
                                sb.append("\n");
                            }
                            rispostaFinale = sb.toString().trim();
                        }
                    }
                    case "FIND_PRICE" -> {
                        String productName = node.path("product").asText("").trim();
                        String unit = node.path("unit").asText("kg").trim().toLowerCase(); // default kg
                        // quantity può essere null
                        BigDecimal quantity = null;
                        if (node.hasNonNull("quantity")) {
                            try {
                                quantity = new BigDecimal(node.get("quantity").asText());
                                if (quantity.compareTo(BigDecimal.ZERO) <= 0)
                                    quantity = null; // ignora 0/negativi
                            } catch (Exception ignore) {
                                /* quantity invalida -> trattala come null */ }
                        }

                        if (!unit.equals("kg")) {
                            rispostaFinale = "Per ora gestisco solo prezzi al kg.";
                            break;
                        }

                        Product p = findBestProduct(productName);
                        if (p == null) {
                            rispostaFinale = "Non ho trovato \"" + productName + "\".";
                            break;
                        }
                        if (p.getPrice() == null) {
                            rispostaFinale = "Il prezzo per \"" + p.getName() + "\" non è disponibile.";
                            break;
                        }

                        BigDecimal unitPrice = p.getPrice(); // €/kg

                        if (quantity == null) {
                            // solo prezzo unitario
                            rispostaFinale = "%s: € %s/kg".formatted(
                                    capitalize(p.getName()),
                                    String.format(Locale.ITALY, "%.2f", unitPrice));
                        } else {
                            // calcolo totale
                            BigDecimal total = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
                            rispostaFinale = "%s: %s kg × € %s/kg = € %s".formatted(
                                    capitalize(p.getName()),
                                    quantity.stripTrailingZeros().toPlainString(),
                                    String.format(Locale.ITALY, "%.2f", unitPrice),
                                    String.format(Locale.ITALY, "%.2f", total));
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

    private static String capitalize(String s) {
        if (s == null || s.isBlank())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String formatFreshnessInfo(Product p) {
        String nome = (p.getName() == null || p.getName().isBlank()) ? "il prodotto richiesto" : p.getName();
        FreshnessType fr = p.getFreshness();
        SourceType src = p.getSource();

        // Costruisci pezzetti di testo opzionali
        List<String> provenienza = new ArrayList<>();
        if (p.getOriginArea() != null && !p.getOriginArea().isBlank())
            provenienza.add(p.getOriginArea());
        if (p.getFaoArea() != null && !p.getFaoArea().isBlank())
            provenienza.add("FAO " + p.getFaoArea());
        if (p.getLandingPort() != null && !p.getLandingPort().isBlank())
            provenienza.add("porto " + p.getLandingPort());
        if (p.getOriginCountry() != null && !p.getOriginCountry().isBlank())
            provenienza.add(p.getOriginCountry());

        String provenienzaTxt = provenienza.isEmpty() ? null : String.join(", ", provenienza);

        String fonteTxt = (src == null) ? null : switch (src) {
            case WILD_CAUGHT -> "pescate";
            case FARMED -> "di allevamento";
        };

        String prezzoTxt = (p.getPrice() != null)
                ? "Prezzo: € " + String.format(java.util.Locale.ITALY, "%.2f", p.getPrice()) + "/kg."
                : null;

        String catchDateTxt = (p.getCatchDate() != null) ? " Pescate il " + p.getCatchDate() + "." : "";
        String tmcTxt = (p.getBestBefore() != null && fr == FreshnessType.FROZEN) ? " TMC: " + p.getBestBefore() + "."
                : "";
        String lavorazione = (p.getProcessing() != null && !p.getProcessing().isBlank())
                ? " Lavorazione: " + p.getProcessing() + "."
                : "";

        // Risposte discorsive
        if (fr == FreshnessType.FROZEN) {
            // Sì → surgelato
            StringBuilder sb = new StringBuilder("Sì, ");
            sb.append(nome.toLowerCase()).append(" sono surgelate");
            if (fonteTxt != null)
                sb.append(", ").append(fonteTxt);
            if (provenienzaTxt != null)
                sb.append(", provenienza: ").append(provenienzaTxt);
            sb.append(".");
            if (prezzoTxt != null)
                sb.append(" ").append(prezzoTxt);
            sb.append(tmcTxt).append(lavorazione);
            if (Boolean.TRUE.equals(p.getOnOffer()))
                sb.append(" Attualmente sono in offerta.");
            return sb.toString().trim();
        } else if (fr == FreshnessType.FRESH) {
            // No → fresche
            StringBuilder sb = new StringBuilder("No, ");
            sb.append(nome.toLowerCase()).append(" non sono surgelate: oggi sono fresche");
            if (fonteTxt != null)
                sb.append(", ").append(fonteTxt);
            if (provenienzaTxt != null)
                sb.append(", provenienza: ").append(provenienzaTxt);
            sb.append(".").append(catchDateTxt);
            if (prezzoTxt != null)
                sb.append(" ").append(prezzoTxt);
            sb.append(lavorazione);
            if (Boolean.TRUE.equals(p.getOnOffer()))
                sb.append(" Attualmente sono in offerta.");
            return sb.toString().trim();
        } else {
            // Informazione non disponibile
            StringBuilder sb = new StringBuilder();
            sb.append("Per ").append(nome.toLowerCase())
                    .append(" al momento non ho un’informazione certa sulla freschezza");
            if (fonteTxt != null)
                sb.append("; risultano ").append(fonteTxt);
            if (provenienzaTxt != null)
                sb.append("; provenienza: ").append(provenienzaTxt);
            sb.append(".");
            if (prezzoTxt != null)
                sb.append(" ").append(prezzoTxt);
            sb.append(lavorazione);
            return sb.toString().trim();
        }
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
