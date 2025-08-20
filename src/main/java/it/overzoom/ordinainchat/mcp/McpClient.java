// src/main/java/it/overzoom/ordinainchat/mcp/McpClient.java
package it.overzoom.ordinainchat.mcp;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// it/overzoom/ordinainchat/mcp/McpClient.java (servizio 1)
@Component
public class McpClient {

    private final RestTemplate rest;
    private final ObjectMapper om;
    private final String baseUrl;
    private final String apiKey;

    public McpClient(
            @Value("${mcp.server.base-url:http://localhost:5000/api/mcp}") String baseUrl,
            @Value("${mcp.api-key}") String apiKey,
            ObjectMapper om) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.om = om;
        this.rest = new RestTemplate();
        this.rest.setUriTemplateHandler(new DefaultUriBuilderFactory(baseUrl));
    }

    public McpCallResponse call(String tool, JsonNode args, Map<String, String> meta) {
        try {
            McpCallRequest req = new McpCallRequest();
            req.setTool(tool);
            req.setArguments(args);
            req.setMeta(meta);

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.set("X-MCP-KEY", apiKey);
            HttpEntity<McpCallRequest> entity = new HttpEntity<>(req, h);

            ResponseEntity<McpCallResponse> resp = rest.postForEntity("/call", entity, McpCallResponse.class);
            return resp.getBody();
        } catch (Exception e) {
            McpCallResponse err = new McpCallResponse();
            err.setOk(false);
            err.setError("client_error: " + e.getMessage());
            return err;
        }
    }
}
