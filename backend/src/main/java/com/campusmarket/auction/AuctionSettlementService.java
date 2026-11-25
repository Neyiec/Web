package com.campusmarket.auction;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campusmarket.auction.AuctionSettledEvent.Outcome;
import com.campusmarket.item.MarketItemMapper;

@Service
public class AuctionSettlementService {
    private final AuctionSettlementMapper auctions;
    private final ApplicationEventPublisher events;
    private final MarketItemMapper items;

    public AuctionSettlementService(AuctionSettlementMapper auctions, ApplicationEventPublisher events,
                                    MarketItemMapper items) {
        this.auctions = auctions;
        this.events = events;
        this.items = items;
    }

    @Transactional
    public void settle(long auctionId) {
        Long candidate = auctions.itemId(auctionId);
        if (candidate == null || items.lock(candidate) == null) {
            return;
        }
        Long itemId = auctions.lockDueAuction(auctionId);
        if (itemId == null) {
            return;
        }

        var winningBid = auctions.findWinningBid(auctionId);
        Outcome outcome = winningBid == null ? Outcome.UNSOLD : Outcome.SETTLED;
        String itemStatus = winningBid == null ? "UNSOLD" : "RESERVED";
        if (auctions.finishItem(itemId, itemStatus) != 1) {
            throw new IllegalStateException("Auction item is no longer available for settlement");
        }

        Long reservationId = null;
        if (winningBid != null) {
            auctions.createReservation(itemId, auctionId, winningBid.bidderId(), winningBid.amount());
            reservationId = auctions.findReservationId(auctionId);
        }
        auctions.finishAuction(auctionId, outcome.name(), winningBid == null ? null : winningBid.amount());
        events.publishEvent(new AuctionSettledEvent(auctionId, itemId, reservationId, outcome));
    }
}
