package org.organicprogramming.holons;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Resolve holons to ready gRPC channels. */
public final class Connect {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private static final Map<ManagedChannel, StartedHandle> STARTED = new IdentityHashMap<>();

    private Connect() {
    }

    public record ConnectOptions(Duration timeout, String transport, boolean start, Path portFile) {
        public ConnectOptions {
            timeout = timeout != null && !timeout.isNegative() && !timeout.isZero() ? timeout : DEFAULT_TIMEOUT;
            transport = transport != null && !transport.isBlank() ? transport.trim().toLowerCase() : "tcp";
        }

        public ConnectOptions() {
            this(DEFAULT_TIMEOUT, "tcp", true, null);
        }
    }

    private record StartedHandle(Process process, boolean ephemeral) {
    }

    private record StartedProcess(String uri, Process process) {
    }

    public static ManagedChannel connect(String target) throws IOException {
        return connectInternal(target, new ConnectOptions(), true);
    }

    public static ManagedChannel connect(String target, ConnectOptions options) throws IOException {
        return connectInternal(target, options != null ? options : new ConnectOptions(), false);
    }

    public static void disconnect(ManagedChannel channel) {
        if (channel == null) {
            return;
        }

        StartedHandle handle;
        synchronized (STARTED) {
            handle = STARTED.remove(channel);
        }

        channel.shutdownNow();
        try {
            channel.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (handle != null && handle.ephemeral()) {
            stopProcess(handle.process());
        }
    }

    private static ManagedChannel connectInternal(String target, ConnectOptions options, boolean ephemeral)
            throws IOException {
        String trimmed = target == null ? "" : target.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("target is required");
        }

        if (!"tcp".equals(options.transport())) {
            throw new IllegalArgumentException("unsupported transport \"" + options.transport() + "\"");
        }

        if (isDirectTarget(trimmed)) {
            return dialReady(normalizeDialTarget(trimmed), options.timeout());
        }

        Optional<Discover.HolonEntry> entryOpt = Discover.findBySlug(trimmed);
        if (entryOpt.isEmpty()) {
            throw new IllegalArgumentException("holon \"" + trimmed + "\" not found");
        }
        Discover.HolonEntry entry = entryOpt.get();

        Path portFile = options.portFile() != null ? options.portFile() : defaultPortFilePath(entry.slug());
        String reusable = usablePortFile(portFile, options.timeout());
        if (reusable != null) {
            return dialReady(normalizeDialTarget(reusable), options.timeout());
        }
        if (!options.start()) {
            throw new IllegalStateException("holon \"" + trimmed + "\" is not running");
        }

        String binaryPath = resolveBinaryPath(entry);
        StartedProcess started = startTcpHolon(binaryPath, options.timeout());

        ManagedChannel channel;
        try {
            channel = dialReady(normalizeDialTarget(started.uri()), options.timeout());
        } catch (IOException | RuntimeException e) {
            stopProcess(started.process());
            throw e;
        }

        if (!ephemeral) {
            try {
                writePortFile(portFile, started.uri());
            } catch (IOException e) {
                disconnect(channel);
                stopProcess(started.process());
                throw e;
            }
        }

        synchronized (STARTED) {
            STARTED.put(channel, new StartedHandle(started.process(), ephemeral));
        }
        return channel;
    }

    private static ManagedChannel dialReady(String target, Duration timeout) throws IOException {
        ManagedChannel channel;
        if (target.startsWith("unix://")) {
            channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        } else {
            HostPort hostPort = parseHostPort(target);
            channel = ManagedChannelBuilder.forAddress(hostPort.host(), hostPort.port()).usePlaintext().build();
        }

        try {
            waitForReady(channel, timeout);
            return channel;
        } catch (IOException | RuntimeException e) {
            channel.shutdownNow();
            throw e;
        }
    }

    private static void waitForReady(ManagedChannel channel, Duration timeout) throws IOException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        ConnectivityState state = channel.getState(true);
        while (state != ConnectivityState.READY) {
            if (state == ConnectivityState.SHUTDOWN) {
                throw new IOException("gRPC channel shut down before becoming ready");
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new IOException("timed out waiting for gRPC readiness");
            }

            CountDownLatch latch = new CountDownLatch(1);
            channel.notifyWhenStateChanged(state, latch::countDown);
            try {
                if (!latch.await(remainingNanos, TimeUnit.NANOSECONDS)) {
                    throw new IOException("timed out waiting for gRPC readiness");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for gRPC readiness", e);
            }
            state = channel.getState(false);
        }
    }

    private static String usablePortFile(Path portFile, Duration timeout) {
        try {
            String raw = Files.readString(portFile).trim();
            if (raw.isEmpty()) {
                Files.deleteIfExists(portFile);
                return null;
            }

            ManagedChannel probe = dialReady(normalizeDialTarget(raw), min(timeout, Duration.ofSeconds(1)));
            disconnect(probe);
            return raw;
        } catch (Exception ignored) {
            try {
                Files.deleteIfExists(portFile);
            } catch (IOException ignoredDelete) {
                // Keep best-effort cleanup semantics.
            }
            return null;
        }
    }

    private static StartedProcess startTcpHolon(String binaryPath, Duration timeout) throws IOException {
        Process process = new ProcessBuilder(binaryPath, "serve", "--listen", "tcp://127.0.0.1:0").start();
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        StringBuilder stderr = new StringBuilder();

        startReader(process.getInputStream(), lines, null);
        startReader(process.getErrorStream(), lines, stderr);

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (!process.isAlive()) {
                throw new IOException("holon exited before advertising an address: " + stderr.toString().trim());
            }

            try {
                String line = lines.poll(50, TimeUnit.MILLISECONDS);
                if (line == null) {
                    continue;
                }
                String uri = firstURI(line);
                if (!uri.isBlank()) {
                    return new StartedProcess(uri, process);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopProcess(process);
                throw new IOException("interrupted while waiting for holon startup", e);
            }
        }

        stopProcess(process);
        throw new IOException("timed out waiting for holon startup");
    }

    private static void startReader(InputStream stream, BlockingQueue<String> lines, StringBuilder capture) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (capture != null) {
                        capture.append(line).append('\n');
                    }
                    lines.offer(line);
                }
            } catch (IOException ignored) {
                // Startup timeout / shutdown paths tolerate closed pipes.
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static String resolveBinaryPath(Discover.HolonEntry entry) {
        if (entry.manifest() == null) {
            throw new IllegalArgumentException("holon \"" + entry.slug() + "\" has no manifest");
        }

        String binary = entry.manifest().artifacts().binary().trim();
        if (binary.isEmpty()) {
            throw new IllegalArgumentException("holon \"" + entry.slug() + "\" has no artifacts.binary");
        }

        Path configured = Path.of(binary);
        if (configured.isAbsolute() && Files.isRegularFile(configured)) {
            return configured.toString();
        }

        Path candidate = entry.dir().resolve(".op").resolve("build").resolve("bin").resolve(configured.getFileName());
        if (Files.isRegularFile(candidate)) {
            return candidate.toString();
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                Path resolved = Path.of(dir).resolve(configured.getFileName().toString());
                if (Files.isRegularFile(resolved) && Files.isExecutable(resolved)) {
                    return resolved.toString();
                }
            }
        }

        throw new IllegalArgumentException("built binary not found for holon \"" + entry.slug() + "\"");
    }

    private static Path defaultPortFilePath(String slug) {
        return Path.of(System.getProperty("user.dir", ".")).resolve(".op").resolve("run").resolve(slug + ".port");
    }

    private static void writePortFile(Path portFile, String uri) throws IOException {
        Files.createDirectories(portFile.getParent());
        Files.writeString(portFile, uri.trim() + System.lineSeparator());
    }

    private static void stopProcess(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }

        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static boolean isDirectTarget(String target) {
        return target.contains("://") || target.contains(":");
    }

    private static String normalizeDialTarget(String target) {
        if (!target.contains("://")) {
            return target;
        }

        Transport.ParsedURI parsed = Transport.parseURI(target);
        if ("tcp".equals(parsed.scheme())) {
            String host = parsed.host() == null || parsed.host().isBlank() || "0.0.0.0".equals(parsed.host())
                    ? "127.0.0.1"
                    : parsed.host();
            return host + ":" + parsed.port();
        }
        if ("unix".equals(parsed.scheme())) {
            return "unix://" + parsed.path();
        }
        return target;
    }

    private static String firstURI(String line) {
        for (String field : line.split("\\s+")) {
            String trimmed = field.trim().replaceAll("^[\"'()\\[\\]{}.,]+|[\"'()\\[\\]{}.,]+$", "");
            if (trimmed.startsWith("tcp://")
                    || trimmed.startsWith("unix://")
                    || trimmed.startsWith("stdio://")
                    || trimmed.startsWith("ws://")
                    || trimmed.startsWith("wss://")) {
                return trimmed;
            }
        }
        return "";
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private record HostPort(String host, int port) {
    }

    private static HostPort parseHostPort(String target) {
        int idx = target.lastIndexOf(':');
        if (idx <= 0 || idx == target.length() - 1) {
            throw new IllegalArgumentException("invalid host:port target \"" + target + "\"");
        }
        return new HostPort(target.substring(0, idx), Integer.parseInt(target.substring(idx + 1)));
    }
}
