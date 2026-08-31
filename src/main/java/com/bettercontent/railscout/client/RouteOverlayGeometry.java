package com.bettercontent.railscout.client;

import com.bettercontent.railscout.navigation.RouteProposal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class RouteOverlayGeometry {
    static final double RAIL_HALF_GAUGE = 0.20;
    static final double SLEEPER_HALF_LENGTH = 0.32;
    private static final double RAIL_HEIGHT = 0.13;
    private static final double SLEEPER_HEIGHT = 0.085;

    private RouteOverlayGeometry() {}

    public static Track build(RouteProposal route) {
        return build(route, 0, null);
    }

    public static Track build(RouteProposal route, int firstStep, @Nullable Vec3 startOverride) {
        int first = Math.max(0, Math.min(firstStep, route.steps().size()));
        List<Vec3> centers = new ArrayList<>(route.steps().size() - first + 1);
        if (startOverride == null) centers.add(center(route.origin(), RAIL_HEIGHT));
        else centers.add(startOverride.add(0, RAIL_HEIGHT, 0));
        for (int index = first; index < route.steps().size(); index++) {
            centers.add(center(route.steps().get(index).railPos(), RAIL_HEIGHT));
        }
        if (centers.size() < 2) return Track.EMPTY;

        List<Vec3> laterals = new ArrayList<>(centers.size());
        for (int index = 0; index < centers.size(); index++) laterals.add(lateral(centers, index));

        List<Segment> rails = new ArrayList<>((centers.size() - 1) * 2);
        for (int index = 1; index < centers.size(); index++) {
            Vec3 previousOffset = laterals.get(index - 1).scale(RAIL_HALF_GAUGE);
            Vec3 currentOffset = laterals.get(index).scale(RAIL_HALF_GAUGE);
            rails.add(new Segment(centers.get(index - 1).add(previousOffset), centers.get(index).add(currentOffset)));
            rails.add(new Segment(centers.get(index - 1).subtract(previousOffset), centers.get(index).subtract(currentOffset)));
        }

        List<Segment> sleepers = new ArrayList<>(centers.size() - 1);
        List<Segment> ties = new ArrayList<>((centers.size() - 1) / 4);
        for (int index = 1; index < centers.size(); index++) {
            Vec3 sleeperCenter = centers.get(index).add(0, SLEEPER_HEIGHT - RAIL_HEIGHT, 0);
            Vec3 half = laterals.get(index).scale(SLEEPER_HALF_LENGTH);
            Segment sleeper = new Segment(sleeperCenter.subtract(half), sleeperCenter.add(half));
            sleepers.add(sleeper);
            int routeStep = first + index;
            if (routeStep % 4 == 0) ties.add(sleeper.raise(0.012));
        }

        List<Segment> pennant = endpointPennant(centers.get(centers.size() - 1),
                laterals.get(laterals.size() - 1));
        return new Track(rails, sleepers, ties, pennant);
    }

    private static Vec3 center(BlockPos pos, double height) {
        return Vec3.atLowerCornerOf(pos).add(0.5, height, 0.5);
    }

    private static Vec3 lateral(List<Vec3> centers, int index) {
        Vec3 incoming = index == 0 ? Vec3.ZERO : horizontalUnit(centers.get(index).subtract(centers.get(index - 1)));
        Vec3 outgoing = index + 1 == centers.size() ? Vec3.ZERO
                : horizontalUnit(centers.get(index + 1).subtract(centers.get(index)));
        Vec3 tangent = incoming.add(outgoing);
        if (tangent.lengthSqr() < 1.0e-6) tangent = incoming.lengthSqr() > 0 ? incoming : outgoing;
        tangent = horizontalUnit(tangent);
        return new Vec3(-tangent.z, 0, tangent.x);
    }

    private static Vec3 horizontalUnit(Vec3 vector) {
        double length = Math.sqrt(vector.x * vector.x + vector.z * vector.z);
        return length < 1.0e-6 ? Vec3.ZERO : new Vec3(vector.x / length, 0, vector.z / length);
    }

    private static List<Segment> endpointPennant(Vec3 endpoint, Vec3 lateral) {
        Vec3 base = endpoint.add(lateral.scale(0.36)).add(0, SLEEPER_HEIGHT - RAIL_HEIGHT, 0);
        Vec3 top = base.add(0, 0.70, 0);
        Vec3 forward = new Vec3(lateral.z, 0, -lateral.x);
        Vec3 tip = top.add(forward.scale(0.34)).add(0, -0.12, 0);
        Vec3 lower = top.add(0, -0.25, 0);
        return List.of(new Segment(base, top), new Segment(top, tip), new Segment(tip, lower));
    }

    public record Segment(Vec3 from, Vec3 to) {
        public Segment raise(double amount) {
            return new Segment(from.add(0, amount, 0), to.add(0, amount, 0));
        }

        public Vec3 midpoint() {
            return from.add(to).scale(0.5);
        }
    }

    public record Track(List<Segment> rails, List<Segment> sleepers,
                        List<Segment> ties, List<Segment> pennant) {
        private static final Track EMPTY = new Track(List.of(), List.of(), List.of(), List.of());

        public Track {
            rails = List.copyOf(rails);
            sleepers = List.copyOf(sleepers);
            ties = List.copyOf(ties);
            pennant = List.copyOf(pennant);
        }
    }
}
