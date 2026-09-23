package com.snoozeshare.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.snoozeshare.app.AppContext;
import com.snoozeshare.app.SceneRouter;
import com.snoozeshare.domain.enums.Role;
import com.snoozeshare.infra.events.events.WalletTransactionRecordedEvent;

class W1FoundationIntegrationTest {

    @Test
    void bootsRegistersRoutesWritesLedgerPublishesAndAudits() throws Exception {
        try (AppContext context = AppContext.create()) {
            var guest = context.userService().register("Guest", "guest@integration.test",
                    Role.GUEST, null);
            var host = context.userService().register("Host", "host@integration.test",
                    Role.HOST, "HOST-DEMO");
            var agent = context.userService().register("Agent", "agent@integration.test",
                    Role.AGENT, "AGENT-DEMO");
            assertTrue(context.walletService().getWallet(guest.userId()).balance()
                    .compareTo(BigDecimal.ZERO) == 0);
            assertTrue(context.walletService().getWallet(host.userId()).balance()
                    .compareTo(BigDecimal.ZERO) == 0);

            context.session().loginAs(guest);
            assertEquals(SceneRouter.GUEST_SCENE,
                    context.sceneRouter().routeFor(context.session().currentRole()));
            context.session().logout();
            context.session().loginAs(agent);
            assertEquals(SceneRouter.ADMIN_SCENE,
                    context.sceneRouter().routeFor(context.session().currentRole()));

            AtomicInteger events = new AtomicInteger();
            context.eventBus().subscribe(WalletTransactionRecordedEvent.class, event -> {
                events.incrementAndGet();
                context.auditService().record(guest.userId(), "WALLET_TRANSACTION_RECORDED",
                        "WALLET_TRANSACTION", event.transactionId(), null, event.walletId());
            });
            var transaction = context.walletService().topUp(guest.userId(),
                    new BigDecimal("25.00"));

            assertEquals(1, events.get());
            assertEquals(0, new BigDecimal("25.00").compareTo(transaction.balanceAfter()));
            assertEquals(1, context.auditService().query(guest.userId(), null,
                    "WALLET_TRANSACTION_RECORDED").size());
        }
    }
}
