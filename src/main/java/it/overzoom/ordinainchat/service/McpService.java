package it.overzoom.ordinainchat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class McpService {

    @Value("${mcp.server.base-url:http://localhost:5000/api/mcp}")
    private String mcpBaseUrl;

    @Value("${mcp.api-key:}")
    private String mcpApiKey;

    public String callMcp(JsonNode payload) {
        String url = mcpBaseUrl + "/call";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (mcpApiKey != null && !mcpApiKey.isBlank()) {
            headers.add("X-MCP-KEY", mcpApiKey);
        }
        HttpEntity<String> entity = new HttpEntity<>(payload.toString(), headers);
        try {
            RestTemplate rt = new RestTemplate();
            ResponseEntity<String> res = rt.postForEntity(url, entity, String.class);
            return res.getBody();
        } catch (HttpStatusCodeException ex) {
            return """
                    {"status":"error","message":"client_error: %s","httpStatus":%d,"body":%s}
                    """.formatted(ex.getStatusText(), ex.getStatusCode().value(),
                    jsonSafe(ex.getResponseBodyAsString()));
        } catch (Exception e) {
            return """
                    {"status":"error","message":"client_error: %s"}
                    """.formatted(jsonSafe(e.getMessage()));
        }
    }

    private String jsonSafe(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
