// src/main/java/it/overzoom/ordinainchat/mcp/McpCallRequest.java
package it.overzoom.ordinainchat.mcp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public class McpCallRequest {
    private String tool; // es. "products_search"
    private JsonNode arguments; // payload del tool
    private Map<String, String> meta; // es. phoneNumber, deviceId, telegramUserId

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public JsonNode getArguments() {
        return arguments;
    }

    public void setArguments(JsonNode arguments) {
        this.arguments = arguments;
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, String> meta) {
        this.meta = meta;
    }
}
