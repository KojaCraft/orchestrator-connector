package net.kojacoord.connector.core;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Connector configuration, resolved (in priority order) from environment
 * variables set by the custom Docker image, then {@code data.yml} /
 * {@code game.json} written by the orchestrator's resource_manager, then
 * sensible defaults. This is what makes the connector "adapted to the
 * orchestrator" without any hand configuration.
 */
public final class ConnectorConfig {
    public final String serverName;
    public final String templateId;
    public final UUID serverUuid;
    public final String redisHost;
    public final int redisPort;
    public final String redisPassword;
    public final int heartbeatSeconds;
    /** Routable host address + port this server is reachable on (from the
     *  orchestrator), so the proxy can register it as a backend. */
    public final String address;
    public final int port;

    private ConnectorConfig(String serverName, String templateId, UUID serverUuid,
                            String redisHost, int redisPort, String redisPassword,
                            int heartbeatSeconds, String address, int port) {
        this.serverName = serverName;
        this.templateId = templateId;
        this.serverUuid = serverUuid;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
        this.heartbeatSeconds = heartbeatSeconds;
        this.address = address;
        this.port = port;
    }

    /** @param dataDir the server working directory (where data.yml/game.json live). */
    @SuppressWarnings("unchecked")
    public static ConnectorConfig load(File dataDir) {
        Map<String, Object> data = readYaml(new File(dataDir, "data.yml"));
        Map<String, Object> game = readJson(new File(dataDir, "game.json"));

        String name = env("SERVER_NAME", str(game.get("server-name"), "minecraft"));
        String template = env("TEMPLATE", str(firstNonNull(data.get("data-url"),
                game.get("template-id")), "unknown"));

        String redisHost = env("REDIS_HOST", str(data.get("redis-bungee-ip"), "127.0.0.1"));
        int redisPort = intEnv("REDIS_PORT", intOf(data.get("redis-bungee-port"), 6379));
        String redisPass = env("REDIS_PASSWORD", str(data.get("redis-bungee-password"), ""));

        // Stable identity: env override, else derive deterministically from the
        // server name so restarts keep the same uuid.
        String uuidStr = env("SERVER_UUID", null);
        UUID uuid = uuidStr != null ? safeUuid(uuidStr)
                : UUID.nameUUIDFromBytes(("koja:" + name).getBytes());

        int heartbeat = intEnv("HEARTBEAT_SECONDS", 5);

        // Routable endpoint for proxy backend registration. The orchestrator
        // passes both as env (SERVER_ADDRESS / PORT); fall back to data.yml.
        String address = env("SERVER_ADDRESS", str(data.get("address"), ""));
        int port = intEnv("PORT", intOf(data.get("port"), 0));

        return new ConnectorConfig(name, template, uuid, redisHost, redisPort, redisPass,
                heartbeat, address, port);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static Map<String, Object> readYaml(File f) {
        if (!f.isFile()) return java.util.Collections.emptyMap();
        try (InputStream in = new FileInputStream(f)) {
            Object o = new Yaml().load(in);
            return o instanceof Map ? (Map<String, Object>) o : java.util.Collections.emptyMap();
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    private static Map<String, Object> readJson(File f) {
        if (!f.isFile()) return java.util.Collections.emptyMap();
        try (java.io.Reader r = new java.io.FileReader(f)) {
            Object o = new com.google.gson.Gson().fromJson(r, Map.class);
            return o instanceof Map ? (Map<String, Object>) o : java.util.Collections.emptyMap();
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }
    private static int intEnv(String key, int def) {
        try { return Integer.parseInt(env(key, Integer.toString(def))); }
        catch (NumberFormatException e) { return def; }
    }
    private static String str(Object o, String def) { return o == null ? def : String.valueOf(o); }
    private static int intOf(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
    private static Object firstNonNull(Object a, Object b) { return a != null ? a : b; }
    private static UUID safeUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return UUID.nameUUIDFromBytes(s.getBytes()); }
    }
}
