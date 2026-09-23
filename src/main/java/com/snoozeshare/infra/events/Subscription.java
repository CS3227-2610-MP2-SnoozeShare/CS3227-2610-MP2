package com.snoozeshare.infra.events;

@FunctionalInterface
public interface Subscription {
    void unsubscribe();
}
