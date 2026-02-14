package org.organicprogramming.holons;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificationContractTest {

    @Test
    void certJsonDeclaresDialCapabilitiesAndExecutables() throws IOException {
        String cert = Files.readString(projectRoot().resolve("cert.json"), StandardCharsets.UTF_8);

        assertTrue(cert.contains("\"echo_client\": \"./bin/echo-client\""));
        assertTrue(cert.contains("\"echo_server\": \"./bin/echo-server\""));
        assertTrue(cert.contains("\"grpc_dial_tcp\": true"));
        assertTrue(cert.contains("\"grpc_dial_stdio\": true"));
    }

    @Test
    void echoClientScriptIsRunnableAndTargetsSharedHelper() throws IOException {
        Path script = projectRoot().resolve("bin/echo-client");
        assertTrue(Files.isRegularFile(script));
        assertTrue(Files.isExecutable(script));

        String content = Files.readString(script, StandardCharsets.UTF_8);
        assertTrue(content.contains("cmd/echo-client-go/main.go"));
        assertTrue(content.contains("--sdk java-holons"));
        assertTrue(content.contains("--server-sdk go-holons"));
    }

    @Test
    void echoServerScriptIsRunnable() throws IOException {
        Path script = projectRoot().resolve("bin/echo-server");
        assertTrue(Files.isRegularFile(script));
        assertTrue(Files.isExecutable(script));

        String content = Files.readString(script, StandardCharsets.UTF_8);
        assertTrue(content.contains("./cmd/echo-server"));
        assertTrue(content.contains("--sdk java-holons"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir"));
    }
}
