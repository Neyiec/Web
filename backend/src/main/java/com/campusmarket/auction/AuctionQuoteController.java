package com.campusmarket.auction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuctionQuoteController {
    private final BidMapper bids;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public AuctionQuoteController(BidMapper bids, StringRedisTemplate redis, ObjectMapper json) {
        this.bids = bids;
        this.redis = redis;
        this.json = json;
    }

    @GetMapping("/api/items/{id}/quote")
    public Quote quote(@PathVariable long id) {
        String key = "campus-market:auction-quote:" + id;
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                Quote cached = json.readValue(value, Quote.class);
                if (cached.endsAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    return cached;
                }
            }
        } catch (DataAccessException | JsonProcessingException ignored) {
            // The price display can fall back to the database.
        }

        Quote current = bids.publicQuote(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前拍卖已结束。");
        }
        try {
            redis.opsForValue().set(key, json.writeValueAsString(current), Duration.ofSeconds(3));
        } catch (DataAccessException | JsonProcessingException ignored) {
            // The cached value is only used for display.
        }
        return current;
    }

    public record Quote(long auctionId, BigDecimal highestPrice, BigDecimal nextMinimum, LocalDateTime endsAt) {}
}
