package net.kojacoord.connector.core;

/**
 * Wire protocol constants, mirroring the orchestrator's {@code protocol/intranet.rs}.
 * Messages are published as the string {@code "<packetId>:<json>"} (the
 * orchestrator splits on the first ':'). All values live here so they can be
 * adjusted in one place to match a given orchestrator deployment.
 */
public final class Protocol {
    private Protocol() {}

    // Packet ids (intranet.rs).
    public static final int HEARTBEAT = 0;
    public static final int HELLO_FROM_CLIENT = 1;
    public static final int BYE_FROM_CLIENT = 5;
    public static final int MINECRAFT_SERVER_SYNC = 7;
    public static final int MINECRAFT_SERVER_UPDATE = 8;

    // Channels.
    /** Orchestrator inbound channel; server lifecycle packets are published here. */
    public static final String CHANNEL_SERVER_IN = "global@orchestrator-server";
    /** Periodic server status reports (player counts, health). */
    public static final String CHANNEL_SERVER_STATUS = "orchestrator:server_status";

    /** Build a {@code "<id>:<json>"} frame. */
    public static String frame(int packetId, String json) {
        return packetId + ":" + json;
    }
}
