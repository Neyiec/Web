package com.campusmarket.item;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MarketItemService {
    private static final Set<String> EDITABLE = Set.of("DRAFT", "REJECTED", "OFF_SHELF", "UNSOLD", "ON_SALE");
    private static final Set<String> CATEGORIES = Set.of("BOOKS", "DORM", "ELECTRONICS", "SPORTS", "OTHER");
    private static final Set<String> CONDITIONS = Set.of("NEW", "LIKE_NEW", "GOOD", "FAIR");
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private final MarketItemMapper items;

    public MarketItemService(MarketItemMapper items) {
        this.items = items;
    }

    @Transactional
    public long create(long seller, ItemSubmission input) {
        validate(input);
        items.insert(seller, input);
        long id = items.lastInsertId();
        if (isAuction(input.saleType())) {
            items.createAuction(id, input);
        }
        return id;
    }

    @Transactional
    public void revise(long seller, long id, ItemSubmission input) {
        validate(input);
        var item = lockOwned(seller, id);
        requireState(EDITABLE.contains(item.status()), "当前状态不支持修改。");
        items.closeAuction(id);
        items.revise(id, input);
        if (isAuction(input.saleType())) {
            items.createAuction(id, input);
        }
    }

    @Transactional
    public void submit(long seller, long id) {
        var item = lockOwned(seller, id);
        requireState("DRAFT".equals(item.status()), "请先保存为草稿再提交。");
        items.submit(id);
    }

    @Transactional
    public void withdraw(long seller, long id) {
        var item = lockOwned(seller, id);
        requireState(Set.of("PENDING_REVIEW", "ON_SALE", "AUCTIONING").contains(item.status()), "当前状态不支持下架。");
        requireState(!isAuction(item.saleType()) || !items.hasBids(id), "已有有效出价，不能下架。");
        items.closeAuction(id);
        items.setStatus(id, "OFF_SHELF");
    }

    @Transactional
    public void deleteDraft(long seller, long id) {
        requireState("DRAFT".equals(lockOwned(seller, id).status()), "只有草稿可以删除。");
        items.closeAuction(id);
        items.setStatus(id, "DELETED");
    }

    @Transactional
    public void review(long reviewer, long id, long version, boolean approved, String reason) {
        var item = items.lock(id);
        requireState(item != null && "PENDING_REVIEW".equals(item.status()) && item.version() == version,
                "该商品版本已变更，请刷新审核列表。");
        if (!approved) {
            requireText(reason, 500, "请填写驳回原因，最多 500 字。");
            items.recordReview(id, item.version(), reviewer, "REJECTED", reason.strip());
            items.setStatus(id, "REJECTED");
            return;
        }

        items.recordReview(id, item.version(), reviewer, "APPROVED", null);
        boolean auction = isAuction(item.saleType());
        if (auction && items.openAuction(id) != 1) {
            throw new IllegalStateException("Auction draft is missing");
        }
        items.publish(id, auction ? "AUCTIONING" : "ON_SALE");
    }

    public MarketItem publicItem(long id) {
        var item = items.publicItem(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在或尚未公开。");
        }
        return item;
    }

    MarketItemMapper.LockedItem lockOwned(long seller, long id) {
        var item = items.lock(id);
        if (item == null || item.sellerId() != seller || "DELETED".equals(item.status())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在。");
        }
        return item;
    }

    private void validate(ItemSubmission input) {
        requireText(input.title(), 100, "请输入商品标题，最多 100 字。");
        requireText(input.description(), 4000, "请输入商品描述，最多 4000 字。");
        requireText(input.campus(), 64, "请输入校区，最多 64 字。");
        if (input.category() == null || !CATEGORIES.contains(input.category())
                || input.conditionCode() == null || !CONDITIONS.contains(input.conditionCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择商品分类和成色。");
        }
        if (isAuction(input.saleType())) {
            if (!validPrice(input.startPrice()) || !validPrice(input.bidIncrement()) || input.price() != null
                    || input.durationMinutes() == null || input.durationMinutes() < 1 || input.durationMinutes() > 10080) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写起拍价、加价幅度和 1 至 10080 分钟的时长。");
            }
        } else if (!"FIXED_PRICE".equals(input.saleType()) || !validPrice(input.price())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择出售方式并填写有效价格。");
        }
    }

    public static boolean validPrice(BigDecimal price) {
        return price != null && price.signum() > 0 && price.scale() <= 2 && price.compareTo(MAX_PRICE) <= 0;
    }

    public static void requireState(boolean valid, String message) {
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    public static void requireText(String value, int limit, String message) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private boolean isAuction(String saleType) {
        return "AUCTION".equals(saleType);
    }
}
