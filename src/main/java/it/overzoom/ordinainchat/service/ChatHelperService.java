package it.overzoom.ordinainchat.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class ChatHelperService {
    private final ProductCatalogService productCatalogService;

    public ChatHelperService(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    public String guessProductNameFromText(String raw) {
        if (raw == null)
            return null;
        String lower = raw.toLowerCase(Locale.ITALY);

        for (String prod : productCatalogService.getProductNames()) {
            if (lower.contains(prod.toLowerCase(Locale.ITALY))) {
                return prod;
            }
        }
        return null;
    }
}
