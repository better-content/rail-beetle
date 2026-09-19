package com.bettercontent.railbeetle.navigation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class RouteBlockerTest {
    @Test void eachRouteFailureUsesItsExactStatusMessage() {
        assertEquals("message.rail_beetle.route_blocked.unloaded",
                new RouteBlocker(BlockPos.ZERO, RouteBlocker.Kind.UNLOADED_TERRAIN, RouteBlocker.Action.MOVE_CLOSER).messageKey());
        assertEquals("message.rail_beetle.route_blocked.geometry",
                new RouteBlocker(BlockPos.ZERO, RouteBlocker.Kind.GEOMETRY, RouteBlocker.Action.CLEAR_OR_REPLAN).messageKey());
        assertEquals("message.rail_beetle.route_blocked.materials",
                new RouteBlocker(BlockPos.ZERO, RouteBlocker.Kind.MATERIAL_SHORTAGE, RouteBlocker.Action.RESTOCK).messageKey());
    }

    @Test void classifierDistinguishesUnloadedGeometryAndMaterials() {
        assertEquals(RouteBlocker.Kind.UNLOADED_TERRAIN,
                RouteBlocker.classify(BlockPos.ZERO, false, true, RouteSupplyStatus.READY, true).kind());
        assertEquals(RouteBlocker.Kind.GEOMETRY,
                RouteBlocker.classify(BlockPos.ZERO, true, false, RouteSupplyStatus.READY, true).kind());
        assertEquals(RouteBlocker.Kind.MATERIAL_SHORTAGE,
                RouteBlocker.classify(BlockPos.ZERO, true, true, new RouteSupplyStatus(1, 0, false), true).kind());
        assertNull(RouteBlocker.classify(BlockPos.ZERO, true, true, RouteSupplyStatus.READY, true));
    }

    @Test void unloadedClassificationNeedsNoTerrainQuery() {
        RouteBlocker blocker = RouteBlocker.classify(BlockPos.ZERO, false, false,
                new RouteSupplyStatus(1, 1, true), false);
        assertEquals(RouteBlocker.Kind.UNLOADED_TERRAIN, blocker.kind());
    }
}
