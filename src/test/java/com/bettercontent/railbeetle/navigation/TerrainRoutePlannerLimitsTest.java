package com.bettercontent.railbeetle.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainRoutePlannerLimitsTest {
    @Test
    void nodeLimitScalesPastTheFormerFixedCeiling() {
        assertEquals(262_144, TerrainRoutePlanner.nodeCapFor(64));
        assertEquals(2_097_152, TerrainRoutePlanner.nodeCapFor(256));
    }

    @Test
    void sharedSearchPoolStaysBounded() {
        assertTrue(TerrainRoutePlanner.searchThreadCount() >= 1);
        assertTrue(TerrainRoutePlanner.searchThreadCount() <= 8);
    }
}
