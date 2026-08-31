package com.bettercontent.railscout.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScoutCompatibilityTest {
    @Test
    void createIsAnOptionalDependency() throws IOException {
        try (var stream = ScoutCompatibilityTest.class.getResourceAsStream("/META-INF/mods.toml")) {
            assertNotNull(stream, "processed mods.toml must be on the test class path");
            String metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int create = metadata.indexOf("modId=\"create\"");
            assertTrue(create >= 0, "mods.toml must declare Create compatibility");
            String dependency = metadata.substring(create, Math.min(metadata.length(), create + 160));
            assertTrue(dependency.contains("mandatory=false"), "Create must remain optional");
        }
    }
}
