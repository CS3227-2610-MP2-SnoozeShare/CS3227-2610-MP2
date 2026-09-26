package com.snoozeshare.service;

import java.math.BigDecimal;
import java.util.UUID;

import com.snoozeshare.domain.enums.ResolutionMode;

/**
 * The only class that moves money for agent dispute resolution (C20). One atomic transaction.
 */
public interface DisputeSettlementService {
    Settlement settle(UUID ticketId, ResolutionMode mode, BigDecimal guestRefund, UUID agentId,
                      String reason);
}
