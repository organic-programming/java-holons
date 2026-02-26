package org.organicprogramming.holons;

/**
 * Standard gRPC server runner utilities.
 */
public final class Serve {

    private Serve() {
    }

    /**
     * Parse --listen or --port from command-line args.
     * Returns a transport URI; falls back to {@link Transport#DEFAULT_URI}.
     */
    public static String parseFlags(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--listen".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                return "tcp://:" + args[i + 1];
            }
        }
        return Transport.DEFAULT_URI;
    }
}
