// src/main/java/it/overzoom/ordinainchat/mcp/McpCallResponse.java
package it.overzoom.ordinainchat.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public class McpCallResponse {
    private boolean ok;
    private JsonNode result; // se ok==true
    private String error; // se ok==false

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public JsonNode getResult() {
        return result;
    }

    public void setResult(JsonNode result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
