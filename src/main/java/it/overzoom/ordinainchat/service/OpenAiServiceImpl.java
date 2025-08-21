package it.overzoom.ordinainchat.service;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
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

    @Override
    public String askChatGpt(List<ChatMessage> messages) {
        final String url = "https://api.openai.com/v1/chat/completions";

        // Costruisco la payload come Map così Jackson fa il JSON giusto.
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", openAiModel);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);

        // messages -> List<Map<String,String>>
        List<Map<String, String>> jsonMessages = messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .collect(toList());
        payload.put("messages", jsonMessages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            String body = response.getBody();
            if (body == null || body.isEmpty()) {
                log.error("OpenAI API returned empty response");
                return "❌ OpenAI API returned an empty response.";
            }
            JSONObject obj = new JSONObject(body);
            return obj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

        } catch (HttpStatusCodeException ex) {
            // Loggo il body d’errore per debugging
            log.error("OpenAI API error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            return "❌ Errore chiamando OpenAI (" + ex.getStatusCode() + ").";
        } catch (Exception e) {
            log.error("Errore generico nella chiamata OpenAI", e);
            return "❌ Errore inatteso chiamando OpenAI.";
        }
    }
}
