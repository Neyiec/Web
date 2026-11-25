package com.campusmarket.auction;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.*;

@Mapper
public interface BidMapper {
    @Select("""
            SELECT a.id AS auction_id,a.highest_price,
                   COALESCE(a.highest_price+a.bid_increment,a.start_price) AS next_minimum,a.ends_at
            FROM auctions a JOIN items i ON i.id=a.item_id
            JOIN item_reviews r ON r.item_id=i.id AND r.item_version=i.version AND r.decision='APPROVED'
            WHERE a.open_item_id=#{itemId} AND a.status='OPEN' AND i.status='AUCTIONING'
              AND a.ends_at>UTC_TIMESTAMP(3)
            """)
    AuctionQuoteController.Quote publicQuote(long itemId);

    @Select("""
            SELECT id,start_price,bid_increment,highest_price,(ends_at > UTC_TIMESTAMP(3)) AS accepting
            FROM auctions WHERE open_item_id=#{itemId} AND status='OPEN'
            FOR UPDATE
            """)
    OpenAuction lockOpen(long itemId);

    @Select("""
            SELECT amount FROM auction_bids
            WHERE auction_id=#{auction} AND bidder_id=#{bidder} AND client_request_id=#{request}
            """)
    BigDecimal existing(@Param("auction") long auction, @Param("bidder") long bidder, @Param("request") String request);

    @Insert("""
            INSERT INTO auction_bids(auction_id,bidder_id,client_request_id,amount)
            VALUES(#{auction},#{bidder},#{request},#{amount})
            """)
    void insert(@Param("auction") long auction, @Param("bidder") long bidder,
                @Param("request") String request, @Param("amount") BigDecimal amount);

    @Update("UPDATE auctions SET highest_price=#{amount},version=version+1 WHERE id=#{auction}")
    void updatePrice(@Param("auction") long auction, @Param("amount") BigDecimal amount);

    record OpenAuction(long id, BigDecimal startPrice, BigDecimal bidIncrement, BigDecimal highestPrice,
                       boolean accepting) {}
}
