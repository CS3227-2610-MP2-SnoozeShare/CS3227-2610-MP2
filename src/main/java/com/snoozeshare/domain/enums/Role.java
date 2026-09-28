package com.snoozeshare.domain.enums;

public enum Role {
    GUEST,
    HOST,
    AGENT,
    /** The platform's own non-loginable account; owns the wallet that receives platform fees (W14, C40). */
    SYSTEM
}
