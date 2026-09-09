package com.bettercontent.railbeetle.entity;

public enum BeetleSpeed {
    HALF(2.0 / 20.0),
    NORMAL(4.0 / 20.0),
    DOUBLE(8.0 / 20.0),
    TRIPLE(12.0 / 20.0);

    private final double blocksPerTick;

    BeetleSpeed(double blocksPerTick) {
        this.blocksPerTick = blocksPerTick;
    }

    public double blocksPerTick() {
        return blocksPerTick;
    }
}
