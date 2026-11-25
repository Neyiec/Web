package com.campusmarket.auction;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuctionSettlementMapper {
    @Select("""
            SELECT a.id
            FROM auctions a
            JOIN items i ON i.id = a.item_id AND i.status = 'AUCTIONING'
            WHERE a.status = 'OPEN' AND a.ends_at <= UTC_TIMESTAMP(3)
            ORDER BY a.ends_at, a.id
            LIMIT 50
            """)
    List<Long> findDueAuctionIds();

    @Select("SELECT item_id FROM auctions WHERE id=#{auctionId}")
    Long itemId(long auctionId);

    @Select("""
            SELECT item_id
            FROM auctions
            WHERE id = #{auctionId} AND status = 'OPEN' AND ends_at <= UTC_TIMESTAMP(3)
            FOR UPDATE
            """)
    Long lockDueAuction(@Param("auctionId") long auctionId);

    @Select("""
            SELECT bidder_id, amount
            FROM auction_bids
            WHERE auction_id = #{auctionId}
            ORDER BY amount DESC, id
            LIMIT 1
            FOR UPDATE
            """)
    WinningBid findWinningBid(@Param("auctionId") long auctionId);

    @Update("""
            UPDATE items
            SET status = #{status}
            WHERE id = #{itemId} AND sale_type = 'AUCTION' AND status = 'AUCTIONING'
            """)
    int finishItem(@Param("itemId") long itemId, @Param("status") String status);

    @Update("""
            UPDATE auctions
            SET status = #{status}, highest_price = #{highestPrice,jdbcType=DECIMAL},
                settled_at = UTC_TIMESTAMP(3), version = version + 1
            WHERE id = #{auctionId}
            """)
    void finishAuction(@Param("auctionId") long auctionId, @Param("status") String status,
                       @Param("highestPrice") BigDecimal highestPrice);

    @Insert("""
            INSERT INTO reservations (item_id, buyer_id, auction_id, agreed_price)
            VALUES (#{itemId}, #{bidderId}, #{auctionId}, #{amount})
            """)
    void createReservation(@Param("itemId") long itemId, @Param("auctionId") long auctionId,
                           @Param("bidderId") long bidderId, @Param("amount") BigDecimal amount);

    @Select("SELECT id FROM reservations WHERE auction_id = #{auctionId}")
    long findReservationId(@Param("auctionId") long auctionId);

    @Select("""
            SELECT i.seller_id,r.buyer_id FROM auctions a
            JOIN items i ON i.id=a.item_id
            LEFT JOIN reservations r ON r.auction_id=a.id
            WHERE a.id=#{auctionId}
            """)
    Recipients recipients(long auctionId);

    record WinningBid(long bidderId, BigDecimal amount) {
    }

    record Recipients(long sellerId, Long buyerId) {}
}
