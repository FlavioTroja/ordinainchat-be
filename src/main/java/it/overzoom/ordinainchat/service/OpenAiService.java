package it.overzoom.ordinainchat.service;

import java.util.List;

public interface OpenAiService {

    record ChatMessage(String role, String content) {
    }

    String askChatGpt(List<ChatMessage> messages);

    default String askChatGpt(String text) {
        return askChatGpt(List.of(new ChatMessage("user", text)));
    }
}
