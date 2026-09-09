package com.bettercontent.railbeetle.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BeetleTimingTest {
    @Test
    void slowDepartureMatchesWhistleDuration() {
        assertEquals(25, RailBeetleEntity.DEPARTURE_TICKS);
    }
}
