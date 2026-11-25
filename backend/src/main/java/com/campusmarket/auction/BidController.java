package com.campusmarket.auction;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.campusmarket.item.MarketItemMapper;
import com.campusmarket.security.MarketPrincipal;

import static com.campusmarket.item.MarketItemService.requireState;
import static com.campusmarket.item.MarketItemService.validPrice;

@RestController
@RequestMapping("/api/auctions")
public class BidController {
    private final MarketItemMapper items;
    private final BidMapper bids;

    public BidController(MarketItemMapper items, BidMapper bids) {
        this.items = items;
        this.bids = bids;
    }

    @PostMapping("/{itemId}/bids")
    @Transactional
    public Map<String,BigDecimal> bid(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long itemId,
                                     @RequestBody BidInput input) {
        if (!validPrice(input.amount()) || input.requestId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写有效的出价金额。");
        }
        var item = items.lock(itemId);
        requireState(item != null && "AUCTIONING".equals(item.status()) && item.sellerId() != account.id(),
                "当前商品无法竞拍。");
        var auction = bids.lockOpen(itemId);
        requireState(auction != null && auction.id() == input.auctionId(), "本场拍卖已结束，请刷新。");
        String request = input.requestId().toString();
        BigDecimal prior = bids.existing(auction.id(), account.id(), request);
        if (prior != null) {
            requireState(prior.compareTo(input.amount()) == 0, "该请求编号已用于其他出价金额。");
            return Map.of("highestPrice", auction.highestPrice());
        }
        requireState(auction.accepting(), "本场拍卖已结束。");
        BigDecimal minimum = auction.highestPrice() == null ? auction.startPrice()
                : auction.highestPrice().add(auction.bidIncrement());
        requireState(input.amount().compareTo(minimum) >= 0, "当前最低有效出价为 " + minimum.toPlainString() + " 元。");
        bids.insert(auction.id(), account.id(), request, input.amount());
        bids.updatePrice(auction.id(), input.amount());
        return Map.of("highestPrice", input.amount());
    }

    public record BidInput(long auctionId, UUID requestId, BigDecimal amount) {}
}
