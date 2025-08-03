package it.overzoom.ordinainchat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

import it.overzoom.ordinainchat.service.TelegramService;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    private final TelegramService telegramService;

    public TelegramController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> onUpdate(@RequestBody Update update) {
        // Delega tutta la logica al service
        telegramService.handleUpdate(update);
        // Puoi restituire una risposta vuota o di conferma (Telegram vuole solo un 200)
        return ResponseEntity.ok("OK");
    }
}
