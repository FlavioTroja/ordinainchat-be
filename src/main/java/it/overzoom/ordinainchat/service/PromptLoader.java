package it.overzoom.ordinainchat.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.type.StepType;

@Component
public class PromptLoader {

    private static final String INIT_PATH = "prompts/init-system-prompt.txt";
    private static final String PLACEHOLDER_USER = "{USER_ID}";

    private final AtomicReference<String> initCache = new AtomicReference<>();

    /**
     * Carica l'INIT (una sola volta), sostituendo {USER_ID}.
     * Usa SEMPRE questo contenuto per il bootstrap della conversation (Responses
     * API).
     */
    public String loadInitSystemPrompt(UUID userId) {
        String init = initCache.updateAndGet(curr -> {
            if (curr != null)
                return curr;
            return safeReadClasspath(INIT_PATH);
        });
        return (userId == null)
                ? init
                : init.replace(PLACEHOLDER_USER, userId.toString());
    }

    /**
     * Snippet breve, per-turno, in base allo stato attuale.
     * Lo invii come ulteriore "system" SOLO nel turno corrente (Responses API).
     */
    public String loadDynamicSystemSnippet(StepType step,
            String lastUserUtterance,
            List<Message> recentContext) {
        if (step == null)
            step = StepType.START;

        return switch (step) {
            case START ->
                """
                        CONTESTO: L'utente può chiedere offerte, disponibilità, prezzi o avviare un ordine.
                        OBIETTIVO: se cita un prodotto SENZA kg → chiedi SOLO i kg; se cita kg+nome → cerca l'ID e, se unico match, prepara l'ordine.
                        REGOLA: se l'utente risponde con un numero (o 1,5) dopo tua domanda sui kg, interpretalo come kg (usa punto decimale).
                        """;

            case SELECT_PRODUCT ->
                """
                        OBIETTIVO: ottenere un productId certo.
                        Se hai solo nome → esegui products_search con textSearch (includi sinonimi utili).
                        Se 1 match → conferma breve (nome+prezzo) o passa ai kg; se >1 match → chiedi conferma con max 3 opzioni.
                        Non creare l'ordine senza ID certo.
                        """;

            case SELECT_QUANTITY -> """
                    OBIETTIVO: ottenere quantityKg.
                    Se l'utente risponde con un numero (“2”, “1,5”, “due”), interpretalo come kg (decimale con punto).
                    Valida che sia > 0 e ragionevole; se molto alta, chiedi conferma.
                    Con ID+kg puoi procedere all'ordine o chiedere eventuali addons.
                    """;

            case SELECT_ADDONS -> """
                    OBIETTIVO: raccogliere addons opzionali (sfilettato, a tranci, pulizia base).
                    Non bloccare l'ordine se non specificati; mantieni eventuali note cliente.
                    """;

            case CONFIRM_ORDER -> """
                    OBIETTIVO: confermare e chiamare orders_create.
                    Requisiti minimi: items [{productId, quantityKg}]. Opzionali: note, inSite, bookedSlot.
                    Rispetta il formato JSON singola riga senza null.
                    """;

            case POST_ORDER -> """
                    OBIETTIVO: chiudere con conferma o gestire follow-up (altro prodotto, modifica).
                    Non richiamare MCP se non serve; rispondi in chiaro a meno di nuova azione.
                    """;

            default -> "";
        };
    }

    // --------- Legacy compat (se ti serve ancora il vecchio metodo) ---------
    /** DEPRECATO: usa loadInitSystemPrompt + loadDynamicSystemSnippet */
    @Deprecated
    public String loadSystemPrompt(UUID userId) {
        return loadInitSystemPrompt(userId);
    }

    // --------- utils ---------
    private String safeReadClasspath(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            try (InputStream in = res.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Errore nel caricamento del prompt: " + path, e);
        }
    }
}
