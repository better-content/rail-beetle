package com.bettercontent.railscout.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

public record RouteProposal(
        int id,
        long generation,
        BlockPos origin,
        Direction originHeading,
        BlockPos endpoint,
        List<RouteStep> steps
) {
    public RouteProposal {
        origin = origin.immutable();
        if (!originHeading.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Route origin heading must be horizontal");
        }
        endpoint = endpoint.immutable();
        steps = List.copyOf(steps);
    }

    public int railCount() {
        return steps.size();
    }

    public int supportCount() {
        return (int) steps.stream().filter(step -> step.supportPos() != null).count();
    }
}
