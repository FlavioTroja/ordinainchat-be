// src/main/java/it/overzoom/ordinainchat/search/ProductSearchMapper.java
package it.overzoom.ordinainchat.search;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ProductSearchMapper {
    private ProductSearchMapper() {
    }

    public static ProductSearchCriteria toCriteria(ProductSearchRequest r) {
        ProductSearchCriteria c = new ProductSearchCriteria();
        if (r == null)
            return c;

        c.setSearch(emptyToNull(r.getSearch()));
        c.setOnlyOnOffer(r.getOnlyOnOffer());
        c.setMaxPrice(r.getMaxPrice());
        c.setItems(r.getItems());
        c.setIncludePrepared(r.getIncludePrepared());
        c.setSortType(r.getSortType());

        // freshFromDate (ISO yyyy-MM-dd)
        if (r.getFreshFromDate() != null && !r.getFreshFromDate().isBlank()) {
            c.setFreshFromDate(LocalDate.parse(
                    r.getFreshFromDate().trim(),
                    DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ITALY)));
        }
        return c;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
