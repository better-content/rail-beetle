package com.bettercontent.railscout.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.RailShape;

import javax.annotation.Nullable;

public record RouteStep(BlockPos railPos, RailShape shape, @Nullable BlockPos supportPos) {
    public RouteStep {
        railPos = railPos.immutable();
        supportPos = supportPos == null ? null : supportPos.immutable();
    }
}
