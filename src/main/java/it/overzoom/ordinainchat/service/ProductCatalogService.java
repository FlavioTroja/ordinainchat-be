package it.overzoom.ordinainchat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;

@Service
public class ProductCatalogService {
    private final List<String> productNames = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final McpService mcpService;

    public ProductCatalogService(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @PostConstruct
    public void loadProducts() {
        refreshProducts();
    }

    @Scheduled(fixedDelay = 3600_000) // ogni ora
    public void refreshProducts() {
        try {
            // Chiamata MCP per tutti i prodotti
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("tool", "products_search");
            ObjectNode args = objectMapper.createObjectNode();
            args.put("onlyOnOffer", false);
            payload.set("arguments", args);

            String json = mcpService.callMcp(payload);
            List<String> names = parseNamesFromMcp(json);

            productNames.clear();
            productNames.addAll(names);
            System.out.println("Catalogo prodotti aggiornato: " + productNames.size() + " articoli.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> getProductNames() {
        return productNames;
    }

    private List<String> parseNamesFromMcp(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        List<String> result = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            if (item.hasNonNull("name")) {
                result.add(item.get("name").asText().toLowerCase(Locale.ITALY));
            }
        }
        return result;
    }
}
