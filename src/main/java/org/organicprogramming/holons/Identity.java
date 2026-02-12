package org.organicprogramming.holons;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parse HOLON.md identity files.
 */
public final class Identity {

    private Identity() {
    }

    /** Parsed holon identity. */
    public record HolonIdentity(
            String uuid,
            String givenName,
            String familyName,
            String motto,
            String composer,
            String clade,
            String status,
            String born,
            String lang,
            List<String> parents,
            String reproduction,
            String generatedBy,
            String protoStatus,
            List<String> aliases) {
    }

    /**
     * Parse a HOLON.md file and return its identity.
     */
    @SuppressWarnings("unchecked")
    public static HolonIdentity parseHolon(Path path) throws IOException {
        String text = Files.readString(path);

        if (!text.startsWith("---")) {
            throw new IllegalArgumentException(path + ": missing YAML frontmatter");
        }

        int endIdx = text.indexOf("---", 3);
        if (endIdx < 0) {
            throw new IllegalArgumentException(path + ": unterminated frontmatter");
        }

        String frontmatter = text.substring(3, endIdx).trim();
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(frontmatter);
        if (data == null)
            data = Map.of();

        return new HolonIdentity(
                str(data, "uuid"),
                str(data, "given_name"),
                str(data, "family_name"),
                str(data, "motto"),
                str(data, "composer"),
                str(data, "clade"),
                str(data, "status"),
                str(data, "born"),
                str(data, "lang"),
                list(data, "parents"),
                str(data, "reproduction"),
                str(data, "generated_by"),
                str(data, "proto_status"),
                list(data, "aliases"));
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List)
            return (List<String>) v;
        return Collections.emptyList();
    }
}
