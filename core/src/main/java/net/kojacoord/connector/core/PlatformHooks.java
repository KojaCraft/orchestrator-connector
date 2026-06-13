package net.kojacoord.connector.core;

/**
 * The tiny, version-specific surface each platform adapter must provide. Keeping
 * it this small is what lets a single connector serve every Minecraft version
 * and loader: everything else (Redis, config, lifecycle, packet shapes) lives in
 * the version-agnostic core.
 */
public interface PlatformHooks {
    /** Current online player count. */
    int getPlayerCount();

    /** Maximum slots configured for this server. */
    int getMaxPlayers();

    /** The server working directory (where data.yml / game.json live). */
    java.io.File getDataDir();

    /** Schedule {@code task} to run every {@code seconds} on a platform-safe thread. */
    void scheduleRepeating(int seconds, Runnable task);

    /** Log an info line through the platform logger. */
    void info(String msg);

    /** Log a warning through the platform logger. */
    void warn(String msg, Throwable t);
}
