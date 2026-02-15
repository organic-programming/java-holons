package org.organicprogramming.holons;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificationInteropTest {

    @Test
    void echoClientSupportsWebSocketGrpcDial() throws Exception {
        Path projectRoot = projectRoot();
        Path goHolonsDir = sdkRoot().resolve("go-holons");

        Process goServer = new ProcessBuilder(
                resolveGoBinary(),
                "run",
                "./cmd/echo-server",
                "--listen",
                "ws://127.0.0.1:0/grpc",
                "--sdk",
                "go-holons")
                .directory(goHolonsDir.toFile())
                .redirectErrorStream(false)
                .start();

        try (BufferedReader serverStdout = new BufferedReader(
                new InputStreamReader(goServer.getInputStream(), StandardCharsets.UTF_8))) {
            String wsURI = readLineWithTimeout(serverStdout, Duration.ofSeconds(20));
            assertNotNull(wsURI, "go ws echo server did not emit listen URI");
            assertTrue(wsURI.startsWith("ws://"), "unexpected ws URI: " + wsURI);

            Process javaClient = new ProcessBuilder(
                    projectRoot.resolve("bin/echo-client").toString(),
                    "--message",
                    "cert-l3-ws",
                    wsURI)
                    .directory(projectRoot.toFile())
                    .redirectErrorStream(false)
                    .start();

            assertTrue(javaClient.waitFor(30, TimeUnit.SECONDS), "echo-client did not exit");
            String clientStdout = readAll(javaClient.getInputStream());
            String clientStderr = readAll(javaClient.getErrorStream());
            assertEquals(0, javaClient.exitValue(), clientStderr);
            assertTrue(clientStdout.contains("\"status\":\"pass\""), clientStdout);
            assertTrue(clientStdout.contains("\"response_sdk\":\"go-holons\""), clientStdout);
        } finally {
            destroyProcess(goServer);
        }
    }

    @Test
    void holonRpcServerScriptHandlesEchoRoundTrip() throws Exception {
        Path projectRoot = projectRoot();

        Process javaServer = new ProcessBuilder(
                projectRoot.resolve("bin/holon-rpc-server").toString(),
                "--once")
                .directory(projectRoot.toFile())
                .redirectErrorStream(false)
                .start();

        try (BufferedReader serverStdout = new BufferedReader(
                new InputStreamReader(javaServer.getInputStream(), StandardCharsets.UTF_8))) {
            String wsURL = readLineWithTimeout(serverStdout, Duration.ofSeconds(20));
            assertNotNull(wsURL, "holon-rpc-server did not emit bind URL");

            HolonRPCClient client = new HolonRPCClient(
                    250, 250, 100, 400,
                    2.0, 0.1, 10_000, 10_000);
            client.connect(wsURL);
            Map<String, Object> out = client.invoke(
                    "echo.v1.Echo/Ping",
                    Map.of("message", "hello"));
            assertEquals("hello", out.get("message"));
            assertEquals("java-holons", out.get("sdk"));
            client.close();

            assertTrue(javaServer.waitFor(20, TimeUnit.SECONDS), "holon-rpc-server did not exit");
            String serverStderr = readAll(javaServer.getErrorStream());
            assertEquals(0, javaServer.exitValue(), serverStderr);
        } finally {
            destroyProcess(javaServer);
        }
    }

    private static String resolveGoBinary() {
        Path preferred = Path.of("/Users/bpds/go/go1.25.1/bin/go");
        if (Files.isExecutable(preferred)) {
            return preferred.toString();
        }
        return "go";
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir"));
    }

    private static Path sdkRoot() {
        Path parent = projectRoot().getParent();
        if (parent == null) {
            throw new IllegalStateException("sdk root not found");
        }
        return parent;
    }

    private static void destroyProcess(Process process) throws InterruptedException {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String readLineWithTimeout(BufferedReader reader, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> lineFuture = executor.submit(reader::readLine);
            return lineFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
