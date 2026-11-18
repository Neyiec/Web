package com.campusmarket.item;

import java.math.BigDecimal;

public record CatalogItem(
        long id,
        String title,
        String category,
        String campus,
        String conditionCode,
        String saleType,
        BigDecimal displayPrice,
        boolean hasBids) {
}
