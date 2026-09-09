package com.bettercontent.railbeetle.entity;

public enum BeetleMode {
    STOPPED,
    PLANNING,
    READY,
    DEPARTING,
    AUTO_BUILD,
    MANUAL_FORWARD,
    MANUAL_REVERSE,
    NEUTRAL,
    PAUSED,
    COMPLETE;

    public boolean moves() {
        return this == DEPARTING || this == AUTO_BUILD || this == MANUAL_FORWARD || this == MANUAL_REVERSE;
    }

    public boolean hasActiveRoute() {
        return this == DEPARTING || this == AUTO_BUILD || this == PAUSED;
    }
}
