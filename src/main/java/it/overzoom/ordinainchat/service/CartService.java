package it.overzoom.ordinainchat.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class CartService {

    private final Map<String, List<CartItem>> carts = new ConcurrentHashMap<>();
    private final ObjectMapper om = new ObjectMapper();

    public record CartItem(Long productId, double qty, LocalDate deliveryDate) {
    }

    public ObjectNode sanitizeCartAddArgs(ObjectNode rawArgs, String telegramUserId) {
        ObjectNode safe = om.createObjectNode();
        ArrayNode itemsNode = om.createArrayNode();

        if (rawArgs.has("items") && rawArgs.get("items").isArray()) {
            for (JsonNode n : rawArgs.get("items")) {
                Long productId = n.hasNonNull("productId") ? n.get("productId").asLong() : null;

                // accetta quantity o quantityKg
                double qty = 0d;
                if (n.hasNonNull("quantity")) {
                    qty = n.get("quantity").asDouble();
                } else if (n.hasNonNull("quantityKg")) {
                    qty = n.get("quantityKg").asDouble();
                }

                LocalDate deliveryDate = LocalDate.now();
                if (n.hasNonNull("deliveryDate")) {
                    try {
                        deliveryDate = LocalDate.parse(n.get("deliveryDate").asText());
                    } catch (Exception ignored) {
                    }
                }

                if (productId != null && qty > 0) {
                    ObjectNode safeItem = om.createObjectNode();
                    safeItem.put("productId", productId);
                    // normalizziamo: salviamo come "quantity"
                    safeItem.put("quantity", qty);
                    safeItem.put("deliveryDate", deliveryDate.toString());
                    itemsNode.add(safeItem);
                }
            }
        }
        safe.set("items", itemsNode);
        return safe;
    }

    public void addItemsToCart(ObjectNode safeArgs, String telegramUserId) {
        List<CartItem> cart = carts.computeIfAbsent(telegramUserId, k -> new ArrayList<>());
        for (JsonNode n : safeArgs.withArray("items")) {
            // qui siamo sicuri che "quantity" c'è (sanitize l’ha normalizzato)
            cart.add(new CartItem(
                    n.get("productId").asLong(),
                    n.get("quantity").asDouble(),
                    LocalDate.parse(n.get("deliveryDate").asText())));
        }
    }

    /**
     * Restituisce gli articoli del carrello.
     */
    public List<CartItem> getItems(String telegramUserId) {
        return carts.getOrDefault(telegramUserId, List.of());
    }

    /**
     * Svuota il carrello.
     */
    public void clear(String telegramUserId) {
        carts.remove(telegramUserId);
    }
}
