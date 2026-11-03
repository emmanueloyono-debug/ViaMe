package com.viame.launcher;

/**
 * Non-Application wrapper entry point for IDE execution and fat-JAR packaging.
 * Delegates directly to {@link com.viame.MainApp#main(String[])}.
 */
public final class MainApp {

    private MainApp() {
    }

    public static void main(String[] args) {
        com.viame.MainApp.main(args);
    }
}

