package net.kojacoord.connector.core;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Loader-neutral {@link PlatformHooks} for mod environments. It reads live
 * player counts off the {@code MinecraftServer} instance via reflection, trying
 * the method names used across mapping sets so a single jar covers as many
 * Minecraft versions as possible. Anything it can't resolve falls back to the
 * {@code MAX_PLAYERS} env / game.json value, so the connector never crashes the
 * server even on an unrecognised version.
 *
 * Scheduling is delegated to a daemon thread (loaders differ on tick-thread
 * APIs, and our work is just Redis publishes).
 */
public final class ReflectiveServerHooks implements PlatformHooks {
    private final Object server;       // net.minecraft.server.MinecraftServer
    private final File dataDir;
    private final java.util.logging.Logger log =
            java.util.logging.Logger.getLogger("koja-connector");

    // RCON is the primary, version/loader-neutral source for player counts.
    private final RconClient rcon = rconFromEnv();
    private int[] rconCache;       // {online, max}
    private long rconCacheAt;      // millis

    public ReflectiveServerHooks(Object minecraftServer, File dataDir) {
        this.server = minecraftServer;
        this.dataDir = dataDir;
    }

    private static RconClient rconFromEnv() {
        String pass = System.getenv("RCON_PASSWORD");
        if (pass == null || pass.isEmpty()) return null;
        String host = System.getenv().getOrDefault("RCON_CLI_HOST", "localhost");
        int port = 25575;
        try { port = Integer.parseInt(System.getenv().getOrDefault("RCON_PORT", "25575")); }
        catch (NumberFormatException ignored) {}
        return new RconClient(host, port, pass);
    }

    /** Cached RCON {online, max}, refreshed at most every ~3s, or null. */
    private int[] rconPlayers() {
        if (rcon == null) return null;
        long now = System.currentTimeMillis();
        if (rconCache != null && now - rconCacheAt < 3000) return rconCache;
        int[] r = rcon.queryPlayers();
        if (r != null) { rconCache = r; rconCacheAt = now; }
        return r;
    }

    @Override public int getPlayerCount() {
        int[] r = rconPlayers();
        if (r != null) return r[0];
        Integer n = tryInt(server, "getPlayerCount", "getCurrentPlayerCount", "method_14574");
        if (n != null) return n;
        // Navigate to the player list and size it.
        Object list = tryObj(server, "getPlayerList", "getPlayerManager", "method_3760");
        if (list != null) {
            Integer s = tryInt(list, "getPlayerCount", "getCurrentPlayerCount", "size");
            if (s != null) return s;
            Object players = tryObj(list, "getPlayers", "method_14571");
            if (players instanceof java.util.Collection) return ((java.util.Collection<?>) players).size();
        }
        return 0;
    }

    @Override public int getMaxPlayers() {
        int[] r = rconPlayers();
        if (r != null && r[1] > 0) return r[1];
        Object list = tryObj(server, "getPlayerList", "getPlayerManager", "method_3760");
        if (list != null) {
            Integer m = tryInt(list, "getMaxPlayers", "method_14592");
            if (m != null && m > 0) return m;
        }
        Integer m = tryInt(server, "getMaxPlayers");
        if (m != null && m > 0) return m;
        // Fallback to configured slots.
        String env = System.getenv("MAX_PLAYERS");
        try { if (env != null) return Integer.parseInt(env); } catch (Exception ignored) {}
        return 20;
    }

    @Override public File getDataDir() { return dataDir; }

    @Override public void scheduleRepeating(int seconds, Runnable task) {
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(Math.max(1, seconds) * 1000L); } catch (InterruptedException e) { return; }
                try { task.run(); } catch (Throwable ignored) {}
            }
        }, "koja-connector-heartbeat");
        t.setDaemon(true);
        t.start();
    }

    @Override public void info(String msg) { log.info(msg); }
    @Override public void warn(String msg, Throwable e) { log.log(java.util.logging.Level.WARNING, msg, e); }

    // ── reflection helpers ──────────────────────────────────────────────────
    private static Integer tryInt(Object target, String... names) {
        if (target == null) return null;
        for (String n : names) {
            try {
                Method m = target.getClass().getMethod(n);
                Object r = m.invoke(target);
                if (r instanceof Integer) return (Integer) r;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object tryObj(Object target, String... names) {
        if (target == null) return null;
        for (String n : names) {
            try {
                Method m = target.getClass().getMethod(n);
                Object r = m.invoke(target);
                if (r != null) return r;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
