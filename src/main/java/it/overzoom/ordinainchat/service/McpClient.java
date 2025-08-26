package it.overzoom.ordinainchat.service;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface McpClient {
    String call(ObjectNode payload);
}
