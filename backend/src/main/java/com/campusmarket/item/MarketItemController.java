package com.campusmarket.item;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.campusmarket.security.MarketPrincipal;

@RestController
@RequestMapping("/api")
public class MarketItemController {
    private final MarketItemMapper items;
    private final MarketItemService market;

    public MarketItemController(MarketItemMapper items, MarketItemService market) {
        this.items = items;
        this.market = market;
    }

    @GetMapping("/items/{id}")
    public MarketItem details(@PathVariable long id) {
        return market.publicItem(id);
    }

    @GetMapping("/seller/items")
    public List<MarketItem> mine(@AuthenticationPrincipal MarketPrincipal account) {
        return items.sellerItems(account.id());
    }

    @PostMapping("/seller/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@AuthenticationPrincipal MarketPrincipal account, @RequestBody ItemSubmission input) {
        return Map.of("id", market.create(account.id(), input));
    }

    @PutMapping("/seller/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revise(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long id,
                       @RequestBody ItemSubmission input) {
        market.revise(account.id(), id, input);
    }

    @PostMapping("/seller/items/{id}/submit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long id) {
        market.submit(account.id(), id);
    }

    @PostMapping("/seller/items/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long id) {
        market.withdraw(account.id(), id);
    }

    @DeleteMapping("/seller/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long id) {
        market.deleteDraft(account.id(), id);
    }

    @GetMapping("/admin/reviews")
    public List<MarketItem> pending() {
        return items.pendingItems();
    }

    @PostMapping("/admin/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void review(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long id,
                       @RequestBody ReviewInput review) {
        market.review(account.id(), id, review.version(), review.approved(), review.reason());
    }

    public record ReviewInput(long version, boolean approved, String reason) {}
}
