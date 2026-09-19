package com.bettercontent.railbeetle.navigation;

import net.minecraft.core.BlockPos;

/** A loaded-world route failure with a location and the next player action. */
public record RouteBlocker(BlockPos position, Kind kind, Action action) {
    public enum Kind { UNLOADED_TERRAIN, GEOMETRY, MATERIAL_SHORTAGE }
    public enum Action { MOVE_CLOSER, CLEAR_OR_REPLAN, RESTOCK }
}
