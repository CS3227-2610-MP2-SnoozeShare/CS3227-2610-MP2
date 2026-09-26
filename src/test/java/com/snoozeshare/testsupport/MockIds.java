package com.snoozeshare.testsupport;

import java.util.UUID;

/**
 * Identifiers from the committed mock DB (db/seed-mock-data.sql).
 */
public final class MockIds {

    public static final UUID AGENT_AMY = id("a0000000-0000-0000-0000-000000000001");
    public static final UUID AGENT_BEN = id("a0000000-0000-0000-0000-000000000002");
    public static final UUID AGENT_CHEN = id("a0000000-0000-0000-0000-000000000003");

    public static final UUID TICKET_1 = id("d0000000-0000-0000-0000-000000000001");
    public static final UUID TICKET_2 = id("d0000000-0000-0000-0000-000000000002");
    public static final UUID TICKET_3 = id("d0000000-0000-0000-0000-000000000003");
    public static final UUID TICKET_4 = id("d0000000-0000-0000-0000-000000000004");
    public static final UUID TICKET_5 = id("d0000000-0000-0000-0000-000000000005");
    public static final UUID TICKET_6 = id("d0000000-0000-0000-0000-000000000006");

    public static final UUID BOOKING_9 = id("20000000-0000-0000-0000-000000000009");
    public static final UUID BOOKING_10 = id("20000000-0000-0000-0000-000000000010");
    public static final UUID BOOKING_11 = id("20000000-0000-0000-0000-000000000011");
    public static final UUID BOOKING_13 = id("20000000-0000-0000-0000-000000000013");

    public static final UUID GUEST_ARIA = id("c0000000-0000-0000-0000-000000000003");
    public static final UUID GUEST_SOPHIA = id("c0000000-0000-0000-0000-000000000005");
    public static final UUID HOST_PRIYA = id("b0000000-0000-0000-0000-000000000003");
    public static final UUID HOST_DIEGO = id("b0000000-0000-0000-0000-000000000004");

    public static final UUID WALLET_ARIA = id("30000000-0000-0000-0000-000000000003");
    public static final UUID WALLET_SOPHIA = id("30000000-0000-0000-0000-000000000005");
    public static final UUID WALLET_PRIYA = id("30000000-0000-0000-0000-000000000010");
    public static final UUID WALLET_DIEGO = id("30000000-0000-0000-0000-000000000011");

    public static final UUID CATEGORY_CLEANLINESS = id("70000000-0000-0000-0000-000000000001");

    private MockIds() {
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }
}
