package com.bettercontent.railbeetle;

import com.bettercontent.railbeetle.entity.BeetleMode;
import com.bettercontent.railbeetle.entity.BeetleSpeed;
import com.bettercontent.railbeetle.entity.BeetleSupplies;
import com.bettercontent.railbeetle.navigation.TerrainRoutePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.EnumProperty;
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
        assertTrue(BeetleMode.DEPARTING.moves());
        assertTrue(BeetleMode.AUTO_BUILD.moves());
        assertTrue(BeetleMode.MANUAL_FORWARD.moves());
        assertTrue(BeetleMode.MANUAL_REVERSE.moves());
        assertFalse(BeetleMode.PLANNING.moves());
        assertFalse(BeetleMode.PAUSED.moves());
    }

    @Test
    void speedTiersUseTheNewFourBlockPerSecondBase() {
        assertEquals(0.1, BeetleSpeed.HALF.blocksPerTick(), 1.0e-9);
        assertEquals(0.2, BeetleSpeed.NORMAL.blocksPerTick(), 1.0e-9);
        assertEquals(0.4, BeetleSpeed.DOUBLE.blocksPerTick(), 1.0e-9);
    }

    @Test
    void requiresTwoStraightRailsBetweenCorners() {
        assertTrue(TerrainRoutePlanner.turnSpacingAllowed(Direction.NORTH, Direction.EAST, false, 0));
        assertFalse(TerrainRoutePlanner.turnSpacingAllowed(Direction.NORTH, Direction.EAST, true, 0));
        assertFalse(TerrainRoutePlanner.turnSpacingAllowed(Direction.NORTH, Direction.EAST, true, 1));
        assertTrue(TerrainRoutePlanner.turnSpacingAllowed(Direction.NORTH, Direction.EAST, true, 2));
        assertTrue(TerrainRoutePlanner.turnSpacingAllowed(Direction.NORTH, Direction.NORTH, true, 0));
    }

    @Test
    void selectsOnlyRailsSupportingTheRequiredShape() {
        EnumProperty<RailShape> straightOnly = EnumProperty.create(
                "shape", RailShape.class, shape -> shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST);
        assertFalse(BeetleSupplies.supportsRailShape(straightOnly, RailShape.NORTH_EAST));
        assertTrue(BeetleSupplies.supportsRailShape(straightOnly, RailShape.EAST_WEST));
    }
}
