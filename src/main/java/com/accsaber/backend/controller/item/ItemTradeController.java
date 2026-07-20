package com.accsaber.backend.controller.item;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.item.CreateTradeRequest;
import com.accsaber.backend.model.dto.response.item.TradeResponse;
import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.item.ItemTradeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/trades")
@RequiredArgsConstructor
@Tag(name = "Item Trades")
public class ItemTradeController {

    private final ItemTradeService tradeService;

    @Operation(summary = "Create a pending trade offer to another user")
    @PostMapping
    public ResponseEntity<TradeResponse> create(@Valid @RequestBody CreateTradeRequest req,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        var trade = tradeService.create(me, req.getToUserId(),
                req.getOfferedItems(), req.getRequestedItems(),
                req.getOfferedEssence(), req.getRequestedEssence(), req.getMessage());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMapper.toTradeResponse(trade));
    }

    @Operation(summary = "Accept a pending trade addressed to me")
    @PostMapping("/{id}/accept")
    public ResponseEntity<TradeResponse> accept(@PathVariable UUID id,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(ItemMapper.toTradeResponse(tradeService.accept(id, me)));
    }

    @Operation(summary = "Decline a pending trade addressed to me")
    @PostMapping("/{id}/decline")
    public ResponseEntity<TradeResponse> decline(@PathVariable UUID id,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(ItemMapper.toTradeResponse(tradeService.decline(id, me)));
    }

    @Operation(summary = "Cancel a pending trade I sent")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<TradeResponse> cancel(@PathVariable UUID id,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(ItemMapper.toTradeResponse(tradeService.cancel(id, me)));
    }

    @Operation(summary = "List my pending incoming trade offers")
    @GetMapping("/incoming")
    public ResponseEntity<List<TradeResponse>> incoming(
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(tradeService.listIncomingPending(me).stream()
                .map(ItemMapper::toTradeResponse).toList());
    }

    @Operation(summary = "List my pending outgoing trade offers")
    @GetMapping("/outgoing")
    public ResponseEntity<List<TradeResponse>> outgoing(
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(tradeService.listOutgoingPending(me).stream()
                .map(ItemMapper::toTradeResponse).toList());
    }

    @Operation(summary = "List my trades with filters", description = "AIO endpoint. direction: incoming | outgoing | both (default both). status: comma-separated TradeStatus values, omit for all. Sorted by createdAt desc by default.")
    @GetMapping
    public ResponseEntity<Page<TradeResponse>> listMine(
            @RequestParam(defaultValue = "both") String direction,
            @RequestParam(required = false) List<TradeStatus> status,
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(tradeService.listForUser(me, direction, status, pageable)
                .map(ItemMapper::toTradeResponse));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
