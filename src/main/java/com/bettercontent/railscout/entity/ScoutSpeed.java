package com.bettercontent.railscout.entity;

public enum ScoutSpeed {
    HALF(2.0 / 20.0),
    NORMAL(4.0 / 20.0),
    DOUBLE(8.0 / 20.0);

    private final double blocksPerTick;

    ScoutSpeed(double blocksPerTick) {
        this.blocksPerTick = blocksPerTick;
    }

    public double blocksPerTick() {
        return blocksPerTick;
    }
}
