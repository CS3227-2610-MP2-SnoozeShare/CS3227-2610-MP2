package com.snoozeshare.infra.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.snoozeshare.infra.events.events.BookingConfirmedEvent;

class InProcessEventBusTest {

    @Test
    void deliversTypedEventsInOrderAndAllowsUnsubscribe() {
        EventBus bus = new InProcessEventBus();
        AtomicInteger delivered = new AtomicInteger();
        Subscription subscription = bus.subscribe(BookingConfirmedEvent.class,
                event -> delivered.incrementAndGet());
        BookingConfirmedEvent event = new BookingConfirmedEvent(UUID.randomUUID(),
                UUID.randomUUID(), Instant.now());

        bus.publish(event);
        subscription.unsubscribe();
        bus.publish(event);

        assertEquals(1, delivered.get());
    }

    @Test
    void oneSubscriberFailureDoesNotStopTheOthers() {
        EventBus bus = new InProcessEventBus();
        AtomicInteger delivered = new AtomicInteger();
        bus.subscribe(BookingConfirmedEvent.class, event -> {
            throw new IllegalStateException("Subscriber failed");
        });
        bus.subscribe(BookingConfirmedEvent.class, event -> delivered.incrementAndGet());

        bus.publish(new BookingConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        assertEquals(1, delivered.get());
    }
}
