package com.bettercontent.railscout.entity;

public enum ScoutMode {
    PLANNING,
    READY,
    AUTO_BUILD,
    MANUAL_FORWARD,
    MANUAL_REVERSE,
    PAUSED,
    COMPLETE;

    public boolean moves() {
        return this == AUTO_BUILD || this == MANUAL_FORWARD || this == MANUAL_REVERSE;
    }
}
