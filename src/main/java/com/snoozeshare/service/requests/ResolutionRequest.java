package com.snoozeshare.service.requests;

import java.math.BigDecimal;

import com.snoozeshare.domain.enums.ResolutionMode;

/**
 * An agent's resolution. guestRefund is required for MANUAL and for ACCEPT of a PARTIAL_REFUND/OTHER
 * request; it is ignored for REJECT and derived for the other ACCEPT remedies.
 */
public record ResolutionRequest(ResolutionMode mode, BigDecimal guestRefund, String reason) {
}
