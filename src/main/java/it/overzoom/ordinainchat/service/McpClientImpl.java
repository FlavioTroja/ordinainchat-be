package it.overzoom.ordinainchat.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class McpClientImpl implements McpClient {
    private final McpService mcpService;

    public McpClientImpl(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @Override
    public String call(ObjectNode payload) {
        return mcpService.callMcp(payload);
    }
}
