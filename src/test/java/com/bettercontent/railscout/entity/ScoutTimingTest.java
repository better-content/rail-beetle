package com.bettercontent.railscout.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ScoutTimingTest {
    @Test
    void slowDepartureMatchesWhistleDuration() {
        assertEquals(25, RailScoutEntity.DEPARTURE_TICKS);
    }
}
