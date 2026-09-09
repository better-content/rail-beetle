package com.bettercontent.railbeetle;

import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.bettercontent.railbeetle.upgrade.ModuleKind;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

final class RailBeetleResourceTest {
    @Test void entityMaterialAtlasIsPresentAtMinecraftTextureResolution() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/assets/rail_beetle/textures/entity/rail_beetle.png")) {
            assertNotNull(stream, "missing Rail Beetle entity texture atlas");
            var image = ImageIO.read(stream);
            assertNotNull(image, "Rail Beetle entity texture atlas must be a readable image");
            assertEquals(64, image.getWidth());
            assertEquals(64, image.getHeight());
        }
    }

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
