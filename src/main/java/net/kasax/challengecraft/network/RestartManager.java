package net.kasax.challengecraft.network;

public class RestartManager {
    private static boolean restartPending = false;
    private static String lastWorldName = null;

    public static void setRestartPending(boolean pending, String worldName) {
        restartPending = pending;
        lastWorldName = worldName;
    }

    public static boolean isRestartPending() {
        return restartPending;
    }

    public static String getLastWorldName() {
        return lastWorldName;
    }
}
