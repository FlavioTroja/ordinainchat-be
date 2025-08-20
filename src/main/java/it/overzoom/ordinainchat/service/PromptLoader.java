package it.overzoom.ordinainchat.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptLoader {

    public String loadSystemPrompt(UUID userId) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/system.txt");
            String content = Files.readString(Paths.get(resource.getURI()));
            return content.replace("{USER_ID}", userId.toString());
        } catch (IOException e) {
            throw new RuntimeException("Errore nel caricamento del system prompt", e);
        }
    }
}
