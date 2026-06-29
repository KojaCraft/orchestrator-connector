package net.kojacoord.connector.core;

import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Version-agnostic connector lifecycle. A platform adapter constructs this with
 * its {@link PlatformHooks}, calls {@link #onServerStarted()} once the world is
 * ready and {@link #onServerStopping()} on shutdown. Everything in between
 * (registration, heartbeats, status reports) is handled here.
 */
public final class OrchestratorConnector {
    private final PlatformHooks platform;
    private final ConnectorConfig cfg;
    private final RedisBus bus;
    private final Gson gson = new Gson();
    private volatile boolean started = false;

    public OrchestratorConnector(PlatformHooks platform) {
        this.platform = platform;
        this.cfg = ConnectorConfig.load(platform.getDataDir());
        this.bus = new RedisBus(cfg);
    }

    /** Call when the dedicated server has finished loading and accepts players. */
    public void onServerStarted() {
        started = true;
        platform.info("Orchestrator connector online: server='" + cfg.serverName
                + "' template='" + cfg.templateId + "' redis=" + cfg.redisHost + ":" + cfg.redisPort);

        // Flip the server to RUNNING in the orchestrator (server_mode handles
        // MINECRAFT_SERVER_UPDATE/Start -> RUNNING + proxy registration).
        publishUpdate("Start");

        // Periodic status so the orchestrator's slot accounting + health stay live.
        platform.scheduleRepeating(cfg.heartbeatSeconds, this::publishStatus);
    }

    /** Call on server shutdown so the orchestrator deregisters this server. */
    public void onServerStopping() {
        if (!started) return;
        started = false;
        // Tell the proxy plugin (which registers backends off our status) to
        // drop this server, then the orchestrator to mark it ended.
        publishStatusWith("Closing");
        publishUpdate("End");
        bus.close();
    }

    private void publishUpdate(String action) {
        Map<String, Object> p = new LinkedHashMap<>();
        // server_mode looks this server up by name; uuid is used for host-agent
        // counters and can be supplied via CLIENT_UUID for exact bookkeeping.
        p.put("uuid", clientUuid().toString());
        p.put("server_name", cfg.serverName);
        p.put("action", action);
        p.put("new_weight", playerCountSafe());
        p.put("max_weight", platform.getMaxPlayers());
        bus.publish(Protocol.CHANNEL_SERVER_IN,
                Protocol.frame(Protocol.MINECRAFT_SERVER_UPDATE, gson.toJson(p)));
    }

    private void publishStatus() {
        if (!started) return;
        publishStatusWith("Open");
    }

    /** Publish a server_status frame with the given status. Includes the
     *  routable address+port so the proxy plugin can register this server as a
     *  backend directly from Redis (no HTTP round-trip). */
    private void publishStatusWith(String status) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("server_name", cfg.serverName);
        p.put("status", status);
        p.put("player_count", playerCountSafe());
        p.put("max_players", platform.getMaxPlayers());
        if (cfg.address != null && !cfg.address.isEmpty()) {
            p.put("address", cfg.address);
        }
        if (cfg.port > 0) {
            p.put("port", cfg.port);
        }
        bus.publish(Protocol.CHANNEL_SERVER_STATUS, gson.toJson(p));
    }

    private int playerCountSafe() {
        try { return platform.getPlayerCount(); }
        catch (Throwable t) { return 0; }
    }

    private UUID clientUuid() {
        String env = System.getenv("CLIENT_UUID");
        if (env != null && !env.isEmpty()) {
            try { return UUID.fromString(env); } catch (Exception ignored) {}
        }
        return cfg.serverUuid;
    }
}
