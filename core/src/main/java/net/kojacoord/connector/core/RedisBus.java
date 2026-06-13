package net.kojacoord.connector.core;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;

import java.time.Duration;

/** Thin Redis publisher used to talk to the orchestrator. */
public final class RedisBus implements AutoCloseable {
    private final JedisPool pool;

    public RedisBus(ConnectorConfig cfg) {
        JedisPoolConfig pc = new JedisPoolConfig();
        pc.setMaxTotal(4);
        pc.setMaxWait(Duration.ofSeconds(2));
        String pass = (cfg.redisPassword == null || cfg.redisPassword.isEmpty()) ? null : cfg.redisPassword;
        this.pool = new JedisPool(pc, cfg.redisHost, cfg.redisPort, 2000, pass);
    }

    public void publish(String channel, String message) {
        try (Jedis j = pool.getResource()) {
            j.publish(channel, message);
        } catch (Exception e) {
            // Non-fatal: the orchestrator tolerates missed beats and will
            // re-query. Avoid crashing the server over a transient Redis blip.
            System.err.println("[koja-connector] redis publish failed: " + e.getMessage());
        }
    }

    @Override public void close() {
        try { pool.close(); } catch (Exception ignored) {}
    }
}
