package com.campusmarket.item;

import java.math.BigDecimal;

public record ItemSubmission(String title, String description, String category, String campus,
                             String conditionCode, String saleType, BigDecimal price,
                             BigDecimal startPrice, BigDecimal bidIncrement, Integer durationMinutes) {
}
