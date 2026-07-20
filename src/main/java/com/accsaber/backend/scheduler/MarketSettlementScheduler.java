package com.accsaber.backend.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.market.MarketSettlementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketSettlementScheduler {

    private final MarketSettlementService settlementService;

    @Value("${accsaber.market.settlement-batch:100}")
    private int settlementBatch;

    @Scheduled(fixedDelayString = "${accsaber.scheduler.market-settlement-delay:15000}")
    public void settleDueListings() {
        int settled = 0;
        while (settled < settlementBatch) {
            try {
                if (!settlementService.settleNextDue()) {
                    break;
                }
            } catch (RuntimeException e) {
                log.error("Market settlement failed; the listing will be retried on the next sweep", e);
                break;
            }
            settled++;
        }
        if (settled > 0) {
            log.info("Settled {} market listing(s)", settled);
        }
    }
}
