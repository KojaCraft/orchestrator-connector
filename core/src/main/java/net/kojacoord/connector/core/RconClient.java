package net.kojacoord.connector.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Source RCON client. Used to read the live player count from the
 * server's own {@code list} command — a path that is identical on every
 * Minecraft version and every loader (Vanilla/Paper/Forge/Fabric/…), so the
 * connector doesn't depend on version-specific server internals.
 *
 * No external dependency: the Source RCON protocol is a handful of
 * length-prefixed little-endian packets.
 */
public final class RconClient {
    private static final int TYPE_AUTH = 3;
    private static final int TYPE_EXEC = 2;
    private static final int TYPE_RESPONSE = 0;

    private final String host;
    private final int port;
    private final String password;

    public RconClient(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }

    /** Online/max players parsed from {@code list}, or null if RCON is unavailable. */
    public int[] queryPlayers() {
        if (password == null || password.isEmpty()) return null;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(1500);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            int authId = 1;
            send(out, authId, TYPE_AUTH, password);
            // Some servers send an empty TYPE_RESPONSE before the auth result.
            int[] hdr = readPacketHeader(in);
            if (hdr == null) return null;
            if (hdr[1] == TYPE_RESPONSE) { skipBody(in, hdr[0]); hdr = readPacketHeader(in); if (hdr == null) return null; }
            // Auth failure is signalled by request id == -1.
            if (hdr[2] == -1) return null;
            skipBody(in, hdr[0]);

            send(out, 2, TYPE_EXEC, "list");
            String body = readResponseBody(in);
            return parseList(body);
        } catch (IOException e) {
            return null;
        }
    }

    /** Parse "There are X of a max of Y players online…" tolerantly. */
    static int[] parseList(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("(\\d+)\\D+?(\\d+)").matcher(s);
        if (m.find()) {
            try { return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) }; }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static void send(OutputStream out, int id, int type, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.US_ASCII);
        int len = 4 + 4 + payload.length + 2; // id + type + body + two null terminators
        ByteBuffer buf = ByteBuffer.allocate(4 + len).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(len);
        buf.putInt(id);
        buf.putInt(type);
        buf.put(payload);
        buf.put((byte) 0);
        buf.put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    /** @return {bodyLen, type, id} or null on EOF. bodyLen excludes the 8 header bytes. */
    private static int[] readPacketHeader(DataInputStream in) throws IOException {
        int total = readIntLE(in);
        if (total < 8) return null;
        int id = readIntLE(in);
        int type = readIntLE(in);
        return new int[]{ total - 8, type, id };
    }

    private static void skipBody(DataInputStream in, int bodyLen) throws IOException {
        in.skipBytes(Math.max(0, bodyLen));
    }

    private static String readResponseBody(DataInputStream in) throws IOException {
        int[] hdr = readPacketHeader(in);
        if (hdr == null) return null;
        int bodyLen = hdr[0];
        byte[] body = new byte[Math.max(0, bodyLen)];
        in.readFully(body);
        // Body ends with two null bytes.
        int n = bodyLen >= 2 ? bodyLen - 2 : bodyLen;
        return new String(body, 0, Math.max(0, n), StandardCharsets.UTF_8);
    }

    private static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte(),
            b2 = in.readUnsignedByte(), b3 = in.readUnsignedByte();
        return (b0) | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }
}
