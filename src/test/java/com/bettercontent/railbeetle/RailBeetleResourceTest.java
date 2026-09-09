package com.bettercontent.railbeetle;

import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.bettercontent.railbeetle.upgrade.ModuleKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

final class RailBeetleResourceTest {
    @Test void everyMachineryItemHasANameModelAndTooltip() throws IOException {
        String lang = resource("/assets/rail_beetle/lang/en_us.json");
        for (EngineKind kind : EngineKind.values()) {
            assertTrue(lang.contains("\"engine.rail_beetle." + kind.id() + "\""));
            if (kind.builtIn()) continue;
            assertNotNull(getClass().getResource("/assets/rail_beetle/models/item/" + kind.id() + ".json"));
            assertTrue(lang.contains("\"item.rail_beetle." + kind.id() + "\""));
            assertTrue(lang.contains("\"tooltip.rail_beetle.engine." + kind.id() + "\""));
        }
        for (ModuleKind kind : ModuleKind.values()) {
            assertNotNull(getClass().getResource("/assets/rail_beetle/models/item/" + kind.id() + ".json"));
            assertTrue(lang.contains("\"item.rail_beetle." + kind.id() + "\""));
            assertTrue(lang.contains("\"tooltip.rail_beetle.module." + kind.id() + "\""));
        }
    }

    @Test void allPowerProvidersRemainOptional() throws IOException {
        String metadata = resource("/META-INF/mods.toml");
        for (String mod : new String[]{"create", "powergrid", "ars_nouveau", "bloodmagic",
                "pneumaticcraft", "goety", "malum", "sodiumdynamiclights"}) {
            int start = metadata.indexOf("modId=\"" + mod + "\"");
            assertTrue(start >= 0, "missing optional metadata for " + mod);
            assertTrue(metadata.substring(start, Math.min(metadata.length(), start + 180)).contains("mandatory=false"),
                    mod + " must be optional");
        }
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
