package com.bettercontent.railscout.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

import java.util.List;

public record RouteProposal(
        int id,
        long generation,
        BlockPos origin,
        Direction originHeading,
        BlockPos endpoint,
        List<RouteStep> steps,
        RouteKind kind,
        @Nullable BlockPos beaconTarget
) {
    public RouteProposal {
        origin = origin.immutable();
        if (!originHeading.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Route origin heading must be horizontal");
        }
        endpoint = endpoint.immutable();
        steps = List.copyOf(steps);
        kind = kind == null ? RouteKind.SURVEY : kind;
        beaconTarget = beaconTarget == null ? null : beaconTarget.immutable();
        if ((kind == RouteKind.BEACON) != (beaconTarget != null)) {
            throw new IllegalArgumentException("Beacon routes require exactly one beacon target");
        }
    }

    public RouteProposal(int id, long generation, BlockPos origin, Direction originHeading,
                         BlockPos endpoint, List<RouteStep> steps) {
        this(id, generation, origin, originHeading, endpoint, steps, RouteKind.SURVEY, null);
    }

    public int railCount() {
        return steps.size();
    }

    public int supportCount() {
        return (int) steps.stream().filter(step -> step.supportPos() != null).count();
    }
}
