package it.overzoom.ordinainchat.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

@Component
public class TelegramWebhookInitializer {

    @Value("${telegram.webhook.token}")
    private String botToken;

    @Value("${telegram.webhook.url}")
    private String webhookUrl;

    @PostConstruct
    public void setTelegramWebhook() {
        String url = String.format("https://api.telegram.org/bot%s/setWebhook?url=%s", botToken, webhookUrl);
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.getForObject(url, String.class);
        System.out.println("Telegram setWebhook response: " + response);
    }
}
