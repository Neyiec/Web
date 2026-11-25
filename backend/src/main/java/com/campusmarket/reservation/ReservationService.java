package com.campusmarket.reservation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.campusmarket.item.MarketItemMapper;

import static com.campusmarket.item.MarketItemService.requireState;

@Service
public class ReservationService {
    private final ReservationMapper reservations;
    private final MarketItemMapper items;

    public ReservationService(ReservationMapper reservations, MarketItemMapper items) {
        this.reservations = reservations;
        this.items = items;
    }

    @Transactional
    public long reserve(long buyer, long itemId, UUID requestId) {
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少预约请求编号。");
        }
        var item = items.lock(itemId);
        String key = requestId.toString();
        var match = reservations.existing(buyer, key);
        if (match != null) {
            requireState(match.itemId() == itemId, "该请求编号已用于其他商品。");
            return match.id();
        }
        requireState(item != null && "ON_SALE".equals(item.status()) && "FIXED_PRICE".equals(item.saleType())
                && item.sellerId() != buyer, "该商品当前无法预约。");
        reservations.reserve(itemId, buyer, key, item.price());
        var created = reservations.existing(buyer, key);
        requireState(created != null && created.itemId() == itemId, "预约已被其他请求占用，请刷新。");
        items.setStatus(itemId, "RESERVED");
        return created.id();
    }

    @Transactional
    public void finish(long user, long id, boolean cancel) {
        Long itemId = reservations.itemId(id);
        if (itemId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var item = items.lock(itemId);
        var reservation = reservations.lock(id);
        boolean buyer = reservation.buyerId() == user;
        if (!buyer && reservation.sellerId() != user) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (cancel && "CANCELLED".equals(reservation.status()) || !cancel && "COMPLETED".equals(reservation.status())) {
            return;
        }
        requireState("ACTIVE".equals(reservation.status()) && "RESERVED".equals(item.status()), "预约已结束。");
        if (cancel) {
            reservations.cancel(id);
            items.setStatus(itemId, reservation.auctionId() == null ? "ON_SALE" : "OFF_SHELF");
            return;
        }
        boolean otherConfirmed = buyer ? reservation.sellerConfirmedAt() != null : reservation.buyerConfirmedAt() != null;
        reservations.confirm(id, buyer, otherConfirmed ? "COMPLETED" : "ACTIVE");
        if (otherConfirmed) {
            items.setStatus(itemId, "SOLD");
        }
    }
}
