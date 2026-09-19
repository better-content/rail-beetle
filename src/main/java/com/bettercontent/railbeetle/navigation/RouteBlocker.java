package com.bettercontent.railbeetle.navigation;

import net.minecraft.core.BlockPos;

/** A loaded-world route failure with a location and the next player action. */
public record RouteBlocker(BlockPos position, Kind kind, Action action) {
    public enum Kind { UNLOADED_TERRAIN, GEOMETRY, MATERIAL_SHORTAGE }
    public enum Action { MOVE_CLOSER, CLEAR_OR_REPLAN, RESTOCK }

    /** The player-facing status key for this precise route failure. */
    public String messageKey() {
        return switch (kind) {
            case UNLOADED_TERRAIN -> "message.rail_beetle.route_blocked.unloaded";
            case GEOMETRY -> "message.rail_beetle.route_blocked.geometry";
            case MATERIAL_SHORTAGE -> "message.rail_beetle.route_blocked.materials";
        };
    }
}
