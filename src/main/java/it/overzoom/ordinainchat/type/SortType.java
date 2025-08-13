// src/main/java/it/overzoom/ordinainchat/type/SortType.java
package it.overzoom.ordinainchat.type;

public enum SortType {
    PRICE_ASC,
    PRICE_DESC,
    NAME_ASC,
    NAME_DESC,
    FRESHNESS_DESC, // catchDate desc poi created_at (date) desc
    FRESHNESS_ASC, // catchDate asc poi created_at (date) asc
    OFFER_FIRST
}
