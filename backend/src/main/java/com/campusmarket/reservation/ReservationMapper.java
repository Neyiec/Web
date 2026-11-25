package com.campusmarket.reservation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ReservationMapper {
    String VIEW = """
            SELECT r.id,r.item_id,i.title,i.seller_id,r.buyer_id,r.auction_id,r.agreed_price,
                   r.status,r.buyer_confirmed_at,r.seller_confirmed_at
            FROM reservations r JOIN items i ON i.id=r.item_id
            """;

    @Select(VIEW + "WHERE i.seller_id=#{account} OR r.buyer_id=#{account} ORDER BY r.id DESC")
    List<Reservation> list(long account);

    @Select("SELECT item_id FROM reservations WHERE id=#{id}")
    Long itemId(long id);

    @Select(VIEW + "WHERE r.id=#{id} FOR UPDATE")
    Reservation lock(long id);

    @Select("""
            SELECT id,item_id FROM reservations
            WHERE buyer_id=#{buyer} AND client_request_id=#{requestId}
            """)
    RequestMatch existing(@Param("buyer") long buyer, @Param("requestId") String requestId);

    @Insert("""
            INSERT INTO reservations(item_id,buyer_id,client_request_id,agreed_price)
            VALUES(#{item},#{buyer},#{requestId},#{price})
            ON DUPLICATE KEY UPDATE id=id
            """)
    void reserve(@Param("item") long item, @Param("buyer") long buyer,
                 @Param("requestId") String requestId, @Param("price") BigDecimal price);

    @Update("""
            UPDATE reservations SET
                buyer_confirmed_at=IF(#{buyer},COALESCE(buyer_confirmed_at,UTC_TIMESTAMP(3)),buyer_confirmed_at),
                seller_confirmed_at=IF(#{buyer},seller_confirmed_at,COALESCE(seller_confirmed_at,UTC_TIMESTAMP(3))),
                status=#{status}
            WHERE id=#{id}
            """)
    void confirm(@Param("id") long id, @Param("buyer") boolean buyer, @Param("status") String status);

    @Update("UPDATE reservations SET status='CANCELLED' WHERE id=#{id}")
    void cancel(long id);

    record RequestMatch(long id, long itemId) {}
    record Reservation(long id, long itemId, String title, long sellerId, long buyerId, Long auctionId,
                       BigDecimal agreedPrice, String status, LocalDateTime buyerConfirmedAt,
                       LocalDateTime sellerConfirmedAt) {}
}
