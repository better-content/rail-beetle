package com.bettercontent.railscout.navigation;

import net.minecraft.core.BlockPos;

import java.util.List;

public record RouteProposal(int id, long generation, BlockPos endpoint, List<RouteStep> steps) {
    public RouteProposal {
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
