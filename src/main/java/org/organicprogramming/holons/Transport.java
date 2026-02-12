package org.organicprogramming.holons;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * URI-based listener factory for gRPC servers.
 *
 * <p>
 * Supported transports:
 * <ul>
 * <li>{@code tcp://<host>:<port>} — TCP socket</li>
 * <li>{@code unix://<path>} — Unix domain socket</li>
 * <li>{@code stdio://} — stdin/stdout pipe</li>
 * <li>{@code mem://} — in-process (testing)</li>
 * </ul>
 */
public final class Transport {

    /** Default transport URI when --listen is omitted. */
    public static final String DEFAULT_URI = "tcp://:9090";

    private Transport() {
    }

    /**
     * Extract the scheme from a transport URI.
     */
    public static String scheme(String uri) {
        int idx = uri.indexOf("://");
        return idx >= 0 ? uri.substring(0, idx) : uri;
    }

    /**
     * Parse a transport URI and return a bound ServerSocket.
     *
     * <p>
     * For tcp:// and unix://, returns a real bound socket.
     * For stdio:// and mem://, returns null (callers must handle
     * these transports specially).
     */
    public static ServerSocket listen(String uri) throws IOException {
        if (uri.startsWith("tcp://")) {
            return listenTcp(uri.substring(6));
        } else if (uri.startsWith("unix://")) {
            throw new UnsupportedOperationException(
                    "unix:// not supported by ServerSocket (use gRPC Netty channel)");
        } else if (uri.equals("stdio://") || uri.equals("stdio")) {
            return null; // stdio handled by caller
        } else if (uri.startsWith("mem://")) {
            return null; // in-process handled by caller
        } else {
            throw new IllegalArgumentException(
                    "unsupported transport URI: " + uri);
        }
    }

    /** Bind a TCP server socket. */
    private static ServerSocket listenTcp(String addr) throws IOException {
        String host;
        int port;
        int colonIdx = addr.lastIndexOf(':');
        if (colonIdx >= 0) {
            host = addr.substring(0, colonIdx);
            port = Integer.parseInt(addr.substring(colonIdx + 1));
        } else {
            host = "";
            port = Integer.parseInt(addr);
        }

        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(
                host.isEmpty() ? "0.0.0.0" : host, port));
        return ss;
    }
}
