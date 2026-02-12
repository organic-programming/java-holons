package org.organicprogramming.holons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
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
    }

    @Test
    void defaultUri() {
        assertEquals("tcp://:9090", Transport.DEFAULT_URI);
    }

    @Test
    void tcpListen() throws IOException {
        try (ServerSocket ss = Transport.listen("tcp://127.0.0.1:0")) {
            assertNotNull(ss);
            assertTrue(ss.getLocalPort() > 0);
        }
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
