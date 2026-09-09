package com.bettercontent.railbeetle.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.RailShape;

import javax.annotation.Nullable;
import java.util.List;

public record RouteStep(BlockPos railPos, RailShape shape, List<BlockPos> supportPositions) {
    public RouteStep {
        railPos = railPos.immutable();
        supportPositions = supportPositions.stream().map(BlockPos::immutable).toList();
    }

    public RouteStep(BlockPos railPos, RailShape shape, @Nullable BlockPos supportPos) {
        this(railPos, shape, supportPos == null ? List.of() : List.of(supportPos));
    }

    /** Nearest support retained as a compatibility convenience for simple route consumers. */
    @Nullable
    public BlockPos supportPos() {
        return supportPositions.isEmpty() ? null : supportPositions.get(supportPositions.size() - 1);
    }
}
