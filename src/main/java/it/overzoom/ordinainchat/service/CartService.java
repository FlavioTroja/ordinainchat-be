package it.overzoom.ordinainchat.service;

import java.math.BigDecimal;
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

    public record CartItem(
            Long productId,
            double quantity,
            BigDecimal priceKg, // opzionale
            BigDecimal pricePiece, // opzionale
            BigDecimal priceEur, // totale riga
            LocalDate deliveryDate) {
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
                    safeItem.put("quantity", qty);
                    safeItem.put("deliveryDate", deliveryDate.toString());

                    if (n.hasNonNull("priceKg")) {
                        safeItem.put("priceKg", n.get("priceKg").decimalValue());
                    }
                    if (n.hasNonNull("pricePiece")) {
                        safeItem.put("pricePiece", n.get("pricePiece").decimalValue());
                    }
                    if (n.hasNonNull("priceEur")) {
                        safeItem.put("priceEur", n.get("priceEur").decimalValue());
                    }

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
            BigDecimal priceKg = n.hasNonNull("priceKg") ? n.get("priceKg").decimalValue() : null;
            BigDecimal pricePiece = n.hasNonNull("pricePiece") ? n.get("pricePiece").decimalValue() : null;
            BigDecimal priceEur = n.hasNonNull("priceEur") ? n.get("priceEur").decimalValue() : null;

            cart.add(new CartItem(
                    n.get("productId").asLong(),
                    n.get("quantity").asDouble(),
                    priceKg,
                    pricePiece,
                    priceEur,
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
