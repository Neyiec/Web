package com.campusmarket.item;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketItem(long id, long sellerId, String title, String description, String category,
                         String campus, String conditionCode, String saleType, BigDecimal price,
                         String status, long version, String reviewReason, Long auctionId,
                         BigDecimal startPrice, BigDecimal bidIncrement, BigDecimal highestPrice,
                         Integer durationMinutes, LocalDateTime endsAt, boolean hasImage) {
}
