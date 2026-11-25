package com.campusmarket.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.auction-settlement.enabled", havingValue = "true")
public class AuctionSettlementJob {
    private static final Logger log = LoggerFactory.getLogger(AuctionSettlementJob.class);

    private final AuctionSettlementMapper auctions;
    private final AuctionSettlementService settlement;

    public AuctionSettlementJob(AuctionSettlementMapper auctions, AuctionSettlementService settlement) {
        this.auctions = auctions;
        this.settlement = settlement;
    }

    @Scheduled(fixedDelayString = "${app.auction-settlement.poll-interval}")
    public void settleDueAuctions() {
        for (long auctionId : auctions.findDueAuctionIds()) {
            try {
                settlement.settle(auctionId);
            } catch (RuntimeException failure) {
                log.error("Settlement failed for auction {}", auctionId, failure);
            }
        }
    }
}
