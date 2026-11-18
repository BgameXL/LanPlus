package dev.bgame.lanplus.client;

public final class PauseMenuButtons {

    private PauseMenuButtons() {
    }

    private static boolean hostedInWorld;

    public static void markHostedInWorld() {
        hostedInWorld = true;
    }

    public static void resetHostedInWorld() {
        hostedInWorld = false;
    }

    public static boolean isHostingInWorld() {
        return hostedInWorld;
    }
}
