package com.campusmarket.item;

import java.util.List;

import org.apache.ibatis.annotations.*;

@Mapper
public interface MarketItemMapper {
    String ITEM_VIEW = """
            SELECT i.id, i.seller_id, i.title, i.description, i.category, i.campus, i.condition_code,
                   i.sale_type, i.price, i.status, i.version, r.reason AS review_reason, a.id AS auction_id,
                   a.start_price, a.bid_increment, a.highest_price, a.duration_minutes, a.ends_at,
                   (JSON_LENGTH(i.image_keys) > 0 AND i.image_keys IS NOT NULL) AS has_image
            FROM items i
            LEFT JOIN item_reviews r ON r.item_id = i.id AND r.item_version = i.version
            LEFT JOIN auctions a ON a.open_item_id = i.id
            """;

    @Select(ITEM_VIEW + """
            WHERE i.id = #{id} AND r.decision = 'APPROVED'
              AND i.status IN ('ON_SALE', 'AUCTIONING', 'RESERVED', 'SOLD', 'UNSOLD')
            """)
    MarketItem publicItem(long id);

    @Select(ITEM_VIEW + "WHERE i.seller_id = #{sellerId} AND i.status <> 'DELETED' ORDER BY i.updated_at DESC, i.id DESC")
    List<MarketItem> sellerItems(long sellerId);

    @Select(ITEM_VIEW + "WHERE i.status = 'PENDING_REVIEW' ORDER BY i.updated_at, i.id")
    List<MarketItem> pendingItems();

    @Select("SELECT seller_id, sale_type, status, price, version FROM items WHERE id = #{id} FOR UPDATE")
    LockedItem lock(long id);

    @Insert("""
            INSERT INTO items (seller_id, title, description, category, campus, price, condition_code, sale_type, status)
            VALUES (#{sellerId}, #{input.title}, #{input.description}, #{input.category}, #{input.campus},
                    #{input.price,jdbcType=DECIMAL}, #{input.conditionCode}, #{input.saleType}, 'DRAFT')
            """)
    void insert(@Param("sellerId") long sellerId, @Param("input") ItemSubmission input);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Update("""
            UPDATE items SET title=#{input.title}, description=#{input.description}, category=#{input.category},
                campus=#{input.campus}, price=#{input.price,jdbcType=DECIMAL}, condition_code=#{input.conditionCode},
                sale_type=#{input.saleType}, status='DRAFT', published_at=NULL
            WHERE id=#{id}
            """)
    void revise(@Param("id") long id, @Param("input") ItemSubmission input);

    @Insert("""
            INSERT INTO auctions(item_id, start_price, bid_increment, duration_minutes)
            VALUES (#{id}, #{input.startPrice}, #{input.bidIncrement}, #{input.durationMinutes})
            """)
    void createAuction(@Param("id") long id, @Param("input") ItemSubmission input);

    @Update("UPDATE auctions SET status='CANCELLED', version=version+1 WHERE open_item_id=#{id}")
    void closeAuction(long id);

    @Select("SELECT EXISTS(SELECT 1 FROM auctions WHERE open_item_id=#{id} AND highest_price IS NOT NULL)")
    boolean hasBids(long id);

    @Update("UPDATE items SET status='PENDING_REVIEW', version=version+1 WHERE id=#{id}")
    void submit(long id);

    @Insert("""
            INSERT INTO item_reviews(item_id,item_version,reviewer_id,decision,reason)
            VALUES(#{id},#{version},#{reviewer},#{decision},#{reason,jdbcType=VARCHAR})
            """)
    void recordReview(@Param("id") long id, @Param("version") long version, @Param("reviewer") long reviewer,
                      @Param("decision") String decision, @Param("reason") String reason);

    @Update("""
            UPDATE auctions SET status='OPEN', started_at=UTC_TIMESTAMP(3),
                ends_at=TIMESTAMPADD(MINUTE,duration_minutes,UTC_TIMESTAMP(3)), version=version+1
            WHERE open_item_id=#{id} AND status='DRAFT'
            """)
    int openAuction(long id);

    @Update("UPDATE items SET status=#{status}, published_at=UTC_TIMESTAMP(3) WHERE id=#{id}")
    void publish(@Param("id") long id, @Param("status") String status);

    @Update("UPDATE items SET status=#{status} WHERE id=#{id}")
    void setStatus(@Param("id") long id, @Param("status") String status);

    @Select("SELECT JSON_UNQUOTE(JSON_EXTRACT(image_keys, '$[0]')) FROM items WHERE id=#{id}")
    String imageKey(long id);

    @Select("""
            SELECT JSON_UNQUOTE(JSON_EXTRACT(i.image_keys, '$[0]'))
            FROM items i JOIN item_reviews r ON r.item_id=i.id AND r.item_version=i.version
            WHERE i.id=#{id} AND r.decision='APPROVED' AND i.status IN ('ON_SALE','AUCTIONING','RESERVED','SOLD','UNSOLD')
            """)
    String publicImageKey(long id);

    @Select("SELECT JSON_UNQUOTE(JSON_EXTRACT(image_keys,'$[0]')) FROM items WHERE id=#{id} AND status='PENDING_REVIEW'")
    String pendingImageKey(long id);

    @Update("UPDATE items SET image_keys=JSON_ARRAY(#{key}) WHERE id=#{id}")
    void setImage(@Param("id") long id, @Param("key") String key);

    record LockedItem(long sellerId, String saleType, String status, java.math.BigDecimal price, long version) {}
}
