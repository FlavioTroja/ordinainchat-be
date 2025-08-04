package it.overzoom.ordinainchat.service;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiServiceImpl implements OpenAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiServiceImpl.class);

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.model}")
    private String openAiModel;

    private final RestTemplate restTemplate = new RestTemplate();

    public String askChatGpt(String userMessage) {
        String url = "https://api.openai.com/v1/chat/completions";
        String requestJson = """
                {
                  "model": "%s",
                  "messages": [{"role": "user", "content": "%s"}],
                  "max_tokens": 800,
                  "temperature": 0.7
                }
                """.formatted(openAiModel, userMessage.replace("\"", "\\\""));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        String body = response.getBody();
        log.info("Risposta OpenAI: " + body);
        String answer = "Nessuna risposta";
        try {
            JSONObject obj = new JSONObject(body);
            answer = obj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            log.error("Errore nel parsing della risposta OpenAI", e);
        }
        if (answer == null || answer.trim().isEmpty()) {
            answer = "❌ Non sono riuscito a generare una risposta.";
        }
        return answer;
    }

}
