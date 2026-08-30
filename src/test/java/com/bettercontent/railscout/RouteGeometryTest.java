package com.bettercontent.railscout;

import com.bettercontent.railscout.entity.ScoutMode;
import com.bettercontent.railscout.navigation.TerrainRoutePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RouteGeometryTest {
    @Test
    void derivesStraightCurveAndAscendingShapes() {
        assertEquals(RailShape.EAST_WEST, TerrainRoutePlanner.shapeFor(
                new BlockPos(-1, 0, 0), BlockPos.ZERO, new BlockPos(1, 0, 0)));
        assertEquals(RailShape.SOUTH_EAST, TerrainRoutePlanner.shapeFor(
                new BlockPos(0, 0, 1), BlockPos.ZERO, new BlockPos(1, 0, 0)));
        assertEquals(RailShape.ASCENDING_EAST, TerrainRoutePlanner.shapeFor(
                new BlockPos(-1, 0, 0), BlockPos.ZERO, new BlockPos(1, 1, 0)));
    }

    @Test
    void onlyMovementModesMove() {
        assertTrue(ScoutMode.AUTO_BUILD.moves());
        assertTrue(ScoutMode.MANUAL_FORWARD.moves());
        assertTrue(ScoutMode.MANUAL_REVERSE.moves());
        assertFalse(ScoutMode.PLANNING.moves());
        assertFalse(ScoutMode.PAUSED.moves());
    }
}
