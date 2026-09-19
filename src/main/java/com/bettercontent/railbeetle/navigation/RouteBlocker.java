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

    /** Classifies observed route state without querying or loading terrain. */
    public static RouteBlocker classify(BlockPos position, boolean loaded, boolean supportsIntact,
                                        RouteSupplyStatus supplies, boolean geometryValid) {
        if (!loaded) return new RouteBlocker(position, Kind.UNLOADED_TERRAIN, Action.MOVE_CLOSER);
        if (!supportsIntact) return new RouteBlocker(position, Kind.GEOMETRY, Action.CLEAR_OR_REPLAN);
        if (supplies.hasMissing()) return new RouteBlocker(position, Kind.MATERIAL_SHORTAGE, Action.RESTOCK);
        return geometryValid ? null : new RouteBlocker(position, Kind.GEOMETRY, Action.CLEAR_OR_REPLAN);
    }
}
