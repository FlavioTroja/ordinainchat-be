package it.overzoom.ordinainchat.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.objects.Update;

import it.overzoom.ordinainchat.model.Customer;
import it.overzoom.ordinainchat.model.User;
import it.overzoom.ordinainchat.repository.CustomerRepository;
import it.overzoom.ordinainchat.repository.UserRepository;

@Service
public class TelegramServiceImpl implements TelegramService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final String botToken;
    private final RestTemplate restTemplate = new RestTemplate();

    public TelegramServiceImpl(UserRepository userRepository,
            CustomerRepository customerRepository,
            @Value("${TELEGRAM_BOT_TOKEN}") String botToken) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.botToken = botToken;
    }

    @Override
    public void handleUpdate(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String telegramUserId = String.valueOf(update.getMessage().getFrom().getId());
            User user = userRepository.findByTelegramUserId(telegramUserId).orElse(null);
            String chatId = update.getMessage().getChatId().toString();

            String messaggio;
            if (user == null) {
                user = new User();
                user.setTelegramUserId(telegramUserId);
                userRepository.save(user);
                messaggio = "Benvenuto! Come ti chiami?";
            } else {
                Customer customer = customerRepository.findByUserId(user.getId()).orElse(null);
                if (customer == null) {
                    messaggio = "Come ti chiami? (Per completare la registrazione)";
                } else {
                    messaggio = "Ciao " + customer.getName() + "! Come posso aiutarti oggi?";
                }
            }
            sendMessageToTelegram(chatId, messaggio);
        }
    }

    private void sendMessageToTelegram(String chatId, String messaggio) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", messaggio);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception ex) {
            ex.printStackTrace();
            // Qui puoi fare log error, o retry, ecc.
        }
    }
}
