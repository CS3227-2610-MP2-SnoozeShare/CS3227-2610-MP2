package com.snoozeshare.infra.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class InProcessEventBus implements EventBus {

    private final Map<Class<? extends DomainEvent>, List<Consumer<? extends DomainEvent>>> subscribers =
            new ConcurrentHashMap<>();

    @Override
    public <T extends DomainEvent> Subscription subscribe(Class<T> type, Consumer<T> subscriber) {
        subscribers.computeIfAbsent(type, ignored -> new ArrayList<>()).add(subscriber);
        return () -> subscribers.getOrDefault(type, List.of()).remove(subscriber);
    }

    @Override
    public void publish(DomainEvent event) {
        List<Consumer<? extends DomainEvent>> listeners = subscribers.getOrDefault(event.getClass(),
                List.of());
        for (Consumer<? extends DomainEvent> listener : List.copyOf(listeners)) {
            try {
                deliver(listener, event);
            } catch (RuntimeException ignored) {
                // One integration listener must not prevent the remaining listeners from running.
            }
        }
    }

    private static <T extends DomainEvent> void deliver(Consumer<? extends DomainEvent> listener,
                                                         T event) {
        @SuppressWarnings("unchecked")
        Consumer<T> typed = (Consumer<T>) listener;
        typed.accept(event);
    }
}
