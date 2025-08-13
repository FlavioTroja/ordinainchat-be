// src/main/java/it/overzoom/ordinainchat/controller/dto/FindOffersAction.java
package it.overzoom.ordinainchat.dto;

import java.math.BigDecimal;
import java.util.List;

public record FindOffersAction(
                String action,
                String query,
                BigDecimal maxPrice,
                String sort,
                Integer limit,
                Boolean onlyOnOffer,
                String freshFromDate,
                List<String> items,
                Boolean includePrepared) {
}
