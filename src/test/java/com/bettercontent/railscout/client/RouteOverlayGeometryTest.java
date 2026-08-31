package com.bettercontent.railscout.client;

import com.bettercontent.railscout.navigation.RouteProposal;
import com.bettercontent.railscout.navigation.RouteStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RouteOverlayGeometryTest {
    private static final double EPSILON = 1.0e-6;

    @Test
    void straightTrackUsesTwinRailsAndOneSleeperPerStep() {
        RouteOverlayGeometry.Track track = RouteOverlayGeometry.build(route(
                new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)));

        assertEquals(4, track.rails().size());
        assertEquals(2, track.sleepers().size());
        assertEquals(0.40, track.rails().get(0).from().distanceTo(track.rails().get(1).from()), EPSILON);
        assertEquals(0.64, track.sleepers().get(0).from().distanceTo(track.sleepers().get(0).to()), EPSILON);
    }

    @Test
    void slopeRailsFollowTheFullBlockHeightChange() {
        RouteOverlayGeometry.Track track = RouteOverlayGeometry.build(route(new BlockPos(1, 1, 0)));

        RouteOverlayGeometry.Segment rail = track.rails().get(0);
        assertEquals(1.0, rail.to().y - rail.from().y, EPSILON);
    }

    @Test
    void cornerEndpointSleeperFacesAcrossTheOutgoingTrack() {
        RouteOverlayGeometry.Track track = RouteOverlayGeometry.build(route(
                new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(2, 0, 1)));

        RouteOverlayGeometry.Segment finalSleeper = track.sleepers().get(2);
        assertEquals(0.64, Math.abs(finalSleeper.to().x - finalSleeper.from().x), EPSILON);
        assertEquals(0.0, finalSleeper.to().z - finalSleeper.from().z, EPSILON);
    }

    @Test
    void surveyTiesRepeatEveryFourRouteSteps() {
        List<BlockPos> steps = new ArrayList<>();
        for (int x = 1; x <= 8; x++) steps.add(new BlockPos(x, 0, 0));

        RouteOverlayGeometry.Track track = RouteOverlayGeometry.build(route(steps.toArray(BlockPos[]::new)));

        assertEquals(2, track.ties().size());
        assertEquals(3, track.pennant().size());
    }

    @Test
    void activeTrackStartsAtScoutAndOmitsCompletedSteps() {
        RouteProposal route = route(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(3, 0, 0));
        var start = new net.minecraft.world.phys.Vec3(1.75, 0.05, 0.5);

        RouteOverlayGeometry.Track track = RouteOverlayGeometry.build(route, 2, start);

        assertEquals(2, track.rails().size());
        assertEquals(1, track.sleepers().size());
        assertTrue(Math.abs(track.rails().get(0).from().x - 1.75) < 0.21);
    }

    @Test
    void emptyRouteProducesNoOverlayGeometry() {
        RouteProposal route = new RouteProposal(0, 1L, BlockPos.ZERO, Direction.EAST,
                BlockPos.ZERO, List.of());

        RouteOverlayGeometry.Track track = RouteOverlayGeometry.build(route);

        assertTrue(track.rails().isEmpty());
        assertTrue(track.sleepers().isEmpty());
        assertTrue(track.pennant().isEmpty());
    }

    private static RouteProposal route(BlockPos... positions) {
        List<RouteStep> steps = java.util.Arrays.stream(positions)
                .map(pos -> new RouteStep(pos, RailShape.EAST_WEST, null))
                .toList();
        BlockPos endpoint = positions.length == 0 ? BlockPos.ZERO : positions[positions.length - 1];
        return new RouteProposal(0, 1L, BlockPos.ZERO, Direction.EAST, endpoint, steps);
    }
}
