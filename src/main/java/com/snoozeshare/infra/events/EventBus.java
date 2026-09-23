package com.snoozeshare.infra.events;

import java.util.function.Consumer;

public interface EventBus {
    <T extends DomainEvent> Subscription subscribe(Class<T> type, Consumer<T> subscriber);

    void publish(DomainEvent event);
}
