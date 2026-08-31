package com.bettercontent.railscout;

import com.bettercontent.railscout.navigation.RouteDiversitySelector;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RouteDiversitySelectorTest {
    @Test
    void choosesFarAndSeparatedEndpointsDeterministically() {
        BlockPos origin = BlockPos.ZERO;
        List<BlockPos> candidates = List.of(
                new BlockPos(8, 0, 0), new BlockPos(7, 0, 1),
                new BlockPos(-8, 0, 0), new BlockPos(0, 0, 8),
                new BlockPos(1, 0, 1));

        List<BlockPos> selected = RouteDiversitySelector.select(origin, candidates, value -> value, 3);

        assertEquals(3, selected.size());
        assertTrue(selected.contains(new BlockPos(8, 0, 0)) || selected.contains(new BlockPos(7, 0, 1)));
        assertTrue(selected.contains(new BlockPos(-8, 0, 0)));
        assertTrue(selected.contains(new BlockPos(0, 0, 8)));
        assertEquals(selected, RouteDiversitySelector.select(origin, candidates, value -> value, 3));
    }

    @Test
    void returnsOnlyAvailableDistinctEndpoints() {
        List<BlockPos> selected = RouteDiversitySelector.select(
                BlockPos.ZERO, List.of(new BlockPos(2, 0, 0), new BlockPos(2, 0, 0)), value -> value, 3);
        assertEquals(List.of(new BlockPos(2, 0, 0)), selected);
    }

    @Test
    void addsUpToSevenWellSeparatedEndpoints() {
        List<BlockPos> candidates = List.of(
                new BlockPos(32, 0, 0), new BlockPos(-32, 0, 0),
                new BlockPos(0, 0, 32), new BlockPos(0, 0, -32),
                new BlockPos(24, 0, 24), new BlockPos(-24, 0, 24),
                new BlockPos(24, 0, -24), new BlockPos(-24, 0, -24));

        List<BlockPos> selected = RouteDiversitySelector.select(
                BlockPos.ZERO, candidates, value -> value, 3, 7);

        assertEquals(7, selected.size());
        assertEquals(selected, RouteDiversitySelector.select(
                BlockPos.ZERO, candidates, value -> value, 3, 7));
    }

    @Test
    void filtersClusteredRoutesAfterGuaranteedThree() {
        List<BlockPos> candidates = List.of(
                new BlockPos(32, 0, 0), new BlockPos(-32, 0, 0), new BlockPos(0, 0, 32),
                new BlockPos(31, 0, 1), new BlockPos(30, 0, 2), new BlockPos(1, 0, 31));

        List<BlockPos> selected = RouteDiversitySelector.select(
                BlockPos.ZERO, candidates, value -> value, 3, 7);

        assertEquals(3, selected.size());
    }
}
