package com.campusmarket.item;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CatalogMapper {
    @Select("""
            SELECT i.id, i.title, i.category, i.campus, i.condition_code, i.sale_type,
                   COALESCE(i.price, a.highest_price, a.start_price) AS display_price,
                   (a.highest_price IS NOT NULL) AS has_bids,
                   (COALESCE(JSON_LENGTH(i.image_keys),0) > 0) AS has_image
            FROM items i
            JOIN item_reviews r
                ON r.item_id = i.id AND r.item_version = i.version AND r.decision = 'APPROVED'
            LEFT JOIN auctions a
                ON a.open_item_id = i.id AND a.status = 'OPEN' AND a.ends_at > UTC_TIMESTAMP(3)
            WHERE ((i.sale_type = 'FIXED_PRICE' AND i.status = 'ON_SALE')
                OR (i.sale_type = 'AUCTION' AND i.status = 'AUCTIONING' AND a.id IS NOT NULL))
              AND (#{category} = '' OR i.category = #{category})
              AND (#{keyword} = '' OR LOCATE(#{keyword}, i.title) > 0 OR LOCATE(#{keyword}, i.campus) > 0)
            ORDER BY i.published_at DESC, i.id DESC
            LIMIT 24
            """)
    List<CatalogItem> findRecentPublicItems(@Param("keyword") String keyword, @Param("category") String category);
}
