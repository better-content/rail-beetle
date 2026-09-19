package com.bettercontent.railbeetle.navigation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RouteBlockerTest {
    @Test void eachRouteFailureUsesItsExactStatusMessage() {
        assertEquals("message.rail_beetle.route_blocked.unloaded",
                new RouteBlocker(BlockPos.ZERO, RouteBlocker.Kind.UNLOADED_TERRAIN, RouteBlocker.Action.MOVE_CLOSER).messageKey());
        assertEquals("message.rail_beetle.route_blocked.geometry",
                new RouteBlocker(BlockPos.ZERO, RouteBlocker.Kind.GEOMETRY, RouteBlocker.Action.CLEAR_OR_REPLAN).messageKey());
        assertEquals("message.rail_beetle.route_blocked.materials",
                new RouteBlocker(BlockPos.ZERO, RouteBlocker.Kind.MATERIAL_SHORTAGE, RouteBlocker.Action.RESTOCK).messageKey());
    }
}
