package it.overzoom.ordinainchat.service;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface TelegramService {

    void handleUpdate(Update update);
}
