package com.bettercontent.railbeetle.item;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Static contract for found starter Beetle recovery and one-way package consumption. */
final class StarterBeetleRecoveryTest {
    @Test void placementConsumesPackageMarkerAndEntityDropDoesNotReinstallSupplies() throws Exception {
        String item = Files.readString(Path.of("src/main/java/com/bettercontent/railbeetle/item/RailBeetleItem.java"));
        String entity = Files.readString(Path.of("src/main/java/com/bettercontent/railbeetle/entity/RailBeetleEntity.java"));
        assertTrue(item.contains("StarterBeetlePackage.install(beetle)"));
        assertTrue(item.contains("context.getItemInHand().shrink(1)"));
        assertTrue(entity.contains("inventoryDropped"));
        assertTrue(entity.contains("spawnAtLocation(stack.copy())"));
        assertTrue(entity.contains("getDropItem()"));
    }
}
