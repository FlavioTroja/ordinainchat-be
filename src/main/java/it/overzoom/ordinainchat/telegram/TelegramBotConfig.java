package it.overzoom.ordinainchat.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.webhook.starter.SpringTelegramWebhookBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import it.overzoom.ordinainchat.model.Customer;
import it.overzoom.ordinainchat.model.User;
import it.overzoom.ordinainchat.repository.CustomerRepository;
import it.overzoom.ordinainchat.repository.UserRepository;

@Configuration
public class TelegramBotConfig {

    @Bean
    public SpringTelegramWebhookBot telegramWebhookBot(
            @Value("${telegrambots.webhook-bots[0].bot-path}") String botPath,
            UserRepository userRepository,
            CustomerRepository customerRepository) {

        return SpringTelegramWebhookBot.builder()
                .botPath(botPath)
                .updateHandler(update -> handleUpdate(update, userRepository, customerRepository))
                .setWebhook(() -> {
                    /* Qui puoi mettere logica per setWebhook, se vuoi */ })
                .deleteWebhook(() -> {
                    /* Qui puoi mettere logica per deleteWebhook, se vuoi */ })
                .build();
    }

    private static BotApiMethod<?> handleUpdate(Update update,
            UserRepository userRepository,
            CustomerRepository customerRepository) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String telegramUserId = String.valueOf(update.getMessage().getFrom().getId());
            User user = userRepository.findByTelegramUserId(telegramUserId).orElse(null);
            String chatId = update.getMessage().getChatId().toString();

            if (user == null) {
                user = new User();
                user.setTelegramUserId(telegramUserId);
                userRepository.save(user);
                return new SendMessage(chatId, "Benvenuto! Come ti chiami?");
            } else {
                Customer customer = customerRepository.findByUserId(user.getId()).orElse(null);
                if (customer == null) {
                    return new SendMessage(chatId, "Come ti chiami? (Per completare la registrazione)");
                } else {
                    return new SendMessage(chatId, "Ciao " + customer.getName() + "! Come posso aiutarti oggi?");
                }
            }
        }
        return null;
    }
}
