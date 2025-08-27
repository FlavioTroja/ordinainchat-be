package it.overzoom.ordinainchat.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiServiceImpl implements OpenAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiServiceImpl.class);

    @Value("${openai.api-key}")
    private String openAiApiKey;
    @Value("${openai.model}")
    private String openAiModel;
    @Value("${openai.temperature:0.7}")
    private double temperature;
    @Value("${openai.max-tokens:800}")
    private int maxTokens;

    private final RestTemplate restTemplate = new RestTemplate();

    // ---------- NEW: Conversations ----------
    @Override
    public String createConversation(String title) {
        final String url = "https://api.openai.com/v1/conversations";

        HttpHeaders headers = authJson();

        // Corpo vuoto: l'API crea la conversation senza parametri
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of(), headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
        log.info("OpenAI createConversation response: {}", resp);

        String body = resp.getBody();
        if (body == null || body.isBlank())
            throw new RuntimeException("Empty createConversation response");

        org.json.JSONObject obj = new org.json.JSONObject(body);
        return obj.getString("id"); // es. "conv_abc123"
    }

    @Override
    public void bootstrapConversation(String conversationId, String initSystemPrompt) {
        if (conversationId == null || conversationId.isBlank())
            throw new IllegalArgumentException("conversationId is required");
        if (initSystemPrompt == null || initSystemPrompt.isBlank())
            return; // niente da fare

        final String url = "https://api.openai.com/v1/responses";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", openAiModel);
        payload.put("conversation", conversationId);
        payload.put("store", true);
        payload.put("max_output_tokens", maxTokens);
        payload.put("temperature", temperature);

        // Responses API: "input" è una lista di items {role, content}
        List<Map<String, String>> input = new java.util.ArrayList<>();
        input.add(Map.of("role", "system", "content", initSystemPrompt));
        payload.put("input", input);

        HttpHeaders headers = authJson();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
        log.info("OpenAI bootstrap responses: {}", resp);
        if (resp.getStatusCode().isError())
            throw new RuntimeException("Bootstrap failed: " + resp);
    }

    @Override
    public String askInConversation(String conversationId, List<ChatMessage> messages, boolean store) {
        final String url = "https://api.openai.com/v1/responses";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", openAiModel);
        payload.put("conversation", conversationId);
        payload.put("store", store);
        payload.put("max_output_tokens", maxTokens);
        payload.put("temperature", temperature);

        List<Map<String, String>> input = new java.util.ArrayList<>();
        for (ChatMessage m : messages) {
            input.add(Map.of("role", m.role(), "content", m.content()));
        }
        payload.put("input", input);

        HttpHeaders headers = authJson();
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("OpenAI responses: {}", response);

            String body = response.getBody();
            if (body == null || body.isBlank())
                throw new RuntimeException("Empty responses body");

            return extractAssistantTextFromResponses(body);
        } catch (HttpStatusCodeException ex) {
            log.error("OpenAI responses error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            return "❌ Errore chiamando OpenAI (" + ex.getStatusCode() + ").";
        } catch (Exception e) {
            log.error("Errore generico nella chiamata OpenAI", e);
            return "❌ Errore inatteso chiamando OpenAI.";
        }
    }

    // ---- legacy (puoi deprecarlo) ----
    @Override
    public String askChatGpt(List<ChatMessage> messages) {
        // fallback compatibilità: chiama responses SENZA conversation
        final String url = "https://api.openai.com/v1/responses";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", openAiModel);
        payload.put("store", false);
        payload.put("max_output_tokens", maxTokens);
        payload.put("temperature", temperature);

        List<Map<String, String>> input = new java.util.ArrayList<>();
        for (ChatMessage m : messages) {
            input.add(Map.of("role", m.role(), "content", m.content()));
        }
        payload.put("input", input);

        HttpHeaders headers = authJson();
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("OpenAI responses (legacy path): {}", response);
            String body = response.getBody();
            if (body == null || body.isBlank())
                return "❌ OpenAI API returned an empty response.";
            return extractAssistantTextFromResponses(body);
        } catch (Exception e) {
            log.error("Errore generico Responses", e);
            return "❌ Errore inatteso chiamando OpenAI.";
        }
    }

    // ---------- helpers ----------
    private HttpHeaders authJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);
        return headers;
    }

    /**
     * Responses API: proviamo prima "output_text", altrimenti compattiamo l'array
     * "output".
     */
    private String extractAssistantTextFromResponses(String body) {
        org.json.JSONObject obj = new org.json.JSONObject(body);
        if (obj.has("output_text")) {
            return obj.optString("output_text", "").trim();
        }
        if (obj.has("output")) {
            StringBuilder sb = new StringBuilder();
            var arr = obj.getJSONArray("output");
            for (int i = 0; i < arr.length(); i++) {
                var item = arr.getJSONObject(i);
                // di solito type="message", role="assistant", content:[{type:"output_text",
                // text:"..."}]
                if (item.has("content")) {
                    var contentArr = item.getJSONArray("content");
                    for (int j = 0; j < contentArr.length(); j++) {
                        var c = contentArr.getJSONObject(j);
                        if (c.has("text"))
                            sb.append(c.getString("text"));
                    }
                } else if (item.has("text")) {
                    sb.append(item.getString("text"));
                }
            }
            return sb.toString().trim();
        }
        // fallback: alcuni modelli restituiscono anche choices[0].message.content
        if (obj.has("choices")) {
            var choices = obj.getJSONArray("choices");
            if (!choices.isEmpty()) {
                var msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg != null)
                    return msg.optString("content", "").trim();
            }
        }
        return "";
    }
}
