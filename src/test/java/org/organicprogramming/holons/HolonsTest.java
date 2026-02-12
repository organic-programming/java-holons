package org.organicprogramming.holons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HolonsTest {

    // --- Transport ---

    @Test
    void schemeExtraction() {
        assertEquals("tcp", Transport.scheme("tcp://:9090"));
        assertEquals("unix", Transport.scheme("unix:///tmp/x.sock"));
        assertEquals("stdio", Transport.scheme("stdio://"));
        assertEquals("mem", Transport.scheme("mem://"));
        assertEquals("ws", Transport.scheme("ws://127.0.0.1:8080/grpc"));
        assertEquals("wss", Transport.scheme("wss://example.com:443/grpc"));
    }

    @Test
    void defaultUri() {
        assertEquals("tcp://:9090", Transport.DEFAULT_URI);
    }

    @Test
    void tcpListen() throws IOException {
        Transport.TcpListener lis = assertInstanceOf(
                Transport.TcpListener.class,
                Transport.listen("tcp://127.0.0.1:0"));
        try (var ss = lis.socket()) {
            assertNotNull(ss);
            assertTrue(ss.getLocalPort() > 0);
        }
    }

    @Test
    void parseUriWssDefaultPath() {
        Transport.ParsedURI parsed = Transport.parseURI("wss://example.com:8443");
        assertEquals("wss", parsed.scheme());
        assertEquals("example.com", parsed.host());
        assertEquals(8443, parsed.port());
        assertEquals("/grpc", parsed.path());
        assertTrue(parsed.secure());
    }

    @Test
    void stdioAndMemListenVariants() throws IOException {
        assertTrue(Transport.listen("stdio://") instanceof Transport.StdioListener);
        assertTrue(Transport.listen("mem://") instanceof Transport.MemListener);
    }

    @Test
    void wsListenVariant() throws IOException {
        Transport.WSListener ws = assertInstanceOf(
                Transport.WSListener.class,
                Transport.listen("ws://127.0.0.1:8080/holon"));
        assertEquals("127.0.0.1", ws.host());
        assertEquals(8080, ws.port());
        assertEquals("/holon", ws.path());
        assertFalse(ws.secure());
    }

    @Test
    void unsupportedUri() {
        assertThrows(IllegalArgumentException.class,
                () -> Transport.listen("ftp://host"));
    }

    // --- Serve ---

    @Test
    void parseFlagsListen() {
        assertEquals("tcp://:8080",
                Serve.parseFlags(new String[] { "--listen", "tcp://:8080" }));
    }

    @Test
    void parseFlagsPort() {
        assertEquals("tcp://:3000",
                Serve.parseFlags(new String[] { "--port", "3000" }));
    }

    @Test
    void parseFlagsDefault() {
        assertEquals(Transport.DEFAULT_URI,
                Serve.parseFlags(new String[] {}));
    }

    // --- Identity ---

    @Test
    void parseHolon(@TempDir Path tmp) throws IOException {
        Path holon = tmp.resolve("HOLON.md");
        Files.writeString(holon,
                "---\nuuid: \"abc-123\"\ngiven_name: \"test\"\n" +
                        "family_name: \"Test\"\nmotto: \"A test.\"\n" +
                        "clade: \"deterministic/pure\"\nlang: \"java\"\n" +
                        "---\n# test\n");

        Identity.HolonIdentity id = Identity.parseHolon(holon);
        assertEquals("abc-123", id.uuid());
        assertEquals("test", id.givenName());
        assertEquals("java", id.lang());
    }

    @Test
    void parseMissingFrontmatter(@TempDir Path tmp) throws IOException {
        Path holon = tmp.resolve("HOLON.md");
        Files.writeString(holon, "# No frontmatter\n");
        assertThrows(IllegalArgumentException.class,
                () -> Identity.parseHolon(holon));
    }
}
