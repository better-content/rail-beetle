package com.bettercontent.railbeetle;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RailBeetleInterfaceResourceTest {
    @Test void primaryControlsUsePlainLanguageWithoutRequiringTooltips() throws IOException {
        String lang = languageFile();
        assertTranslation(lang, "screen.rail_beetle.stop.button", "STOP");
        assertTranslation(lang, "screen.rail_beetle.reverse.button", "REVERSE");
        assertTranslation(lang, "screen.rail_beetle.half.button", "2 b/s");
        assertTranslation(lang, "screen.rail_beetle.normal.button", "4 b/s");
        assertTranslation(lang, "screen.rail_beetle.double.button", "8 b/s");
        assertTranslation(lang, "screen.rail_beetle.triple.button", "12 b/s");
        assertTranslation(lang, "screen.rail_beetle.neutral.off", "Free-roll: OFF");
        assertTranslation(lang, "screen.rail_beetle.brake.auto", "Brake: AUTO");
        assertTranslation(lang, "screen.rail_beetle.searchlight.off", "Light: OFF");
    }

    @Test void screenExplainsMachineryStateAndDestructiveCommands() throws IOException {
        String lang = languageFile();
        for (String key : new String[]{
                "screen.rail_beetle.machinery_locked.help.one",
                "screen.rail_beetle.machinery_locked.help.two",
                "screen.rail_beetle.machinery_ready.help.one",
                "screen.rail_beetle.machinery_ready.help.two",
                "screen.rail_beetle.current_mode",
                "screen.rail_beetle.remote.current_mode",
                "screen.rail_beetle.governor_hint.one",
                "screen.rail_beetle.governor_hint.two",
                "screen.rail_beetle.remote.governor_hint"
        }) {
            assertTrue(lang.contains("\"" + key + "\""), "missing explanatory UI copy: " + key);
        }
        assertTrue(lang.contains("This clears any active route"), "reverse must explain that it clears a route");
        assertTrue(lang.contains("Free-roll clears the active route"), "free-roll must explain its side effects");
    }

    private static void assertTranslation(String lang, String key, String value) {
        assertTrue(lang.contains("\"" + key + "\": \"" + value + "\""),
                () -> "expected explicit label for " + key);
    }

    private String languageFile() throws IOException {
        try (var stream = getClass().getResourceAsStream("/assets/rail_beetle/lang/en_us.json")) {
            assertNotNull(stream, "missing English language file");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
