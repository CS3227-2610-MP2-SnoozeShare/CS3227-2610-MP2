package com.snoozeshare.app;

/**
 * Non-{@link javafx.application.Application} entry point so the shaded jar
 * can launch without a {@code module-info.java} on the classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
