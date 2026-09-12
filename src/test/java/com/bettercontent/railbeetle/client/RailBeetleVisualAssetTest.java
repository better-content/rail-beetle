package com.bettercontent.railbeetle.client;

import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.bettercontent.railbeetle.upgrade.ModuleKind;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RailBeetleVisualAssetTest {
    @Test void rectangularFacesPreserveTheMaterialTextureAspectRatio() {
        assertEquals(1.0f, RailBeetleRenderer.fittedSpan(4, 2));
        assertEquals(0.5f, RailBeetleRenderer.fittedSpan(2, 4));
        assertEquals(1.0f, RailBeetleRenderer.fittedSpan(3, 3));
    }

    @Test void everyNonBlockItemUsesItsOwnSixteenPixelInventoryIcon() throws IOException {
        for (String id : inventoryIconIds().toList()) {
            String texturePath = "/assets/rail_beetle/textures/item/" + id + ".png";
            try (var stream = getClass().getResourceAsStream(texturePath)) {
                assertNotNull(stream, "missing inventory texture for " + id);
                var image = ImageIO.read(stream);
                assertNotNull(image, "unreadable inventory texture for " + id);
                assertEquals(16, image.getWidth(), id + " texture width");
                assertEquals(16, image.getHeight(), id + " texture height");
                assertTrue(image.getColorModel().hasAlpha(), id + " must preserve transparency");
            }

            String modelPath = "/assets/rail_beetle/models/item/" + id + ".json";
            try (var stream = getClass().getResourceAsStream(modelPath)) {
                assertNotNull(stream, "missing inventory model for " + id);
                String model = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(model.contains("\"parent\":\"minecraft:item/generated\""),
                        id + " must render as a generated inventory sprite");
                assertTrue(model.contains("\"layer0\":\"rail_beetle:item/" + id + "\""),
                        id + " must reference its matching inventory texture");
            }
        }
    }

    private static Stream<String> inventoryIconIds() {
        Stream<String> baseItems = Stream.of(
                "rail_beetle", "engine_cradle", "compact_module_frame", "dispatch_remote");
        Stream<String> engines = Stream.of(EngineKind.values())
                .filter(kind -> !kind.builtIn())
                .map(EngineKind::id);
        Stream<String> modules = Stream.of(ModuleKind.values()).map(ModuleKind::id);
        return Stream.of(baseItems, engines, modules).flatMap(stream -> stream);
    }
}
