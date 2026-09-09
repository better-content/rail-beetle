package com.bettercontent.railbeetle.upgrade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EngineKindTest {
    @Test
    void resourceAmountsStayCompactAcrossEngineScales() {
        assertEquals("0", EngineKind.compactAmount(0));
        assertEquals("999", EngineKind.compactAmount(999));
        assertEquals("1.6k", EngineKind.compactAmount(1_600));
        assertEquals("144k", EngineKind.compactAmount(144_000));
        assertEquals("18.4M", EngineKind.compactAmount(18_424_576));
    }
}
