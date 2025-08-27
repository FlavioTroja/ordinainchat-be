package it.overzoom.ordinainchat.service;

import java.util.List;

public interface OpenAiService {
    record ChatMessage(String role, String content) {
    }

    /** Crea una conversation e ritorna l'id. */
    String createConversation(String title);

    /** Inietta l'init-system-prompt nella conversation (una volta sola). */
    void bootstrapConversation(String conversationId, String initSystemPrompt);

    /**
     * Invia i messaggi del turno dentro la conversation e ritorna il testo
     * assistant.
     */
    String askInConversation(String conversationId, List<ChatMessage> messages, boolean store);

    // deprecabile: vecchio metodo
    String askChatGpt(List<ChatMessage> messages);
}
