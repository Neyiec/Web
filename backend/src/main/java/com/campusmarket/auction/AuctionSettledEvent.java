package com.campusmarket.auction;

public record AuctionSettledEvent(long auctionId, long itemId, Long reservationId, Outcome outcome) {
    public enum Outcome {
        SETTLED, UNSOLD
    }
}
