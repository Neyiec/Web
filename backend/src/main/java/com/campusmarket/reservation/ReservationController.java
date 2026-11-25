package com.campusmarket.reservation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.campusmarket.security.MarketPrincipal;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService service;
    private final ReservationMapper reservations;

    public ReservationController(ReservationService service, ReservationMapper reservations) {
        this.service = service;
        this.reservations = reservations;
    }

    @GetMapping
    public List<ReservationMapper.Reservation> list(@AuthenticationPrincipal MarketPrincipal account) {
        return reservations.list(account.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Long> reserve(@AuthenticationPrincipal MarketPrincipal account, @RequestBody ReserveInput input) {
        return Map.of("id", service.reserve(account.id(), input.itemId(), input.requestId()));
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long id) {
        service.finish(account.id(), id, false);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long id) {
        service.finish(account.id(), id, true);
    }

    public record ReserveInput(long itemId, UUID requestId) {}
}
