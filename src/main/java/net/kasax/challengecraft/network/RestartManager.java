package net.kasax.challengecraft.network;

public class RestartManager {
    private static boolean restartPending = false;
    private static String lastWorldName = null;
    private static boolean isSinglePlayer = false;

    public static void setRestartPending(boolean pending, String worldName, boolean singlePlayer) {
        restartPending = pending;
        lastWorldName = worldName;
        isSinglePlayer = singlePlayer;
    }

    public static boolean isRestartPending() {
        return restartPending;
    }

    public static String getLastWorldName() {
        return lastWorldName;
    }

    public static boolean isSinglePlayer() {
        return isSinglePlayer;
    }
}
