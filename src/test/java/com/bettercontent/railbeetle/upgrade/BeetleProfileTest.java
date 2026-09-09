package com.bettercontent.railbeetle.upgrade;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BeetleProfileTest {
    @Test void baselineRemainsUseful() {
        BeetleProfile profile = BeetleProfile.from(List.of());
        assertEquals(4.0, profile.maxBlocksPerSecond());
        assertEquals(64, profile.routeCap());
        assertEquals(2, profile.trailingCartLimit());
        assertEquals(1, profile.bridgeWidth());
        assertEquals(4, profile.supportDepth());
        assertEquals(0, profile.remoteRange());
    }

    @Test void secondTierIsTransformativeAndOneFamilyUsesItsHighestTier() {
        BeetleProfile profile = BeetleProfile.from(List.of(
                ModuleKind.HIGH_SPEED_GOVERNOR_I, ModuleKind.HIGH_SPEED_GOVERNOR_II,
                ModuleKind.EXHAUST_RECUPERATOR_II, ModuleKind.BRAKE_MANIFOLD_II,
                ModuleKind.ADHESION_SANDERS_II, ModuleKind.COMPOUND_TORQUE_CLUTCH_II,
                ModuleKind.REINFORCED_DRAWGEAR_II, ModuleKind.TELESCOPIC_SURVEY_ARRAY_II,
                ModuleKind.DISPATCH_RECEIVER_II, ModuleKind.TRESTLE_ERECTOR_II,
                ModuleKind.CAGED_SEARCHLIGHT));
        assertEquals(12.0, profile.maxBlocksPerSecond());
        assertEquals(0.40, profile.efficiencyMultiplier());
        assertEquals(0.08, profile.serviceDeceleration());
        assertEquals(3.0, profile.torqueMultiplier());
        assertEquals(12, profile.trailingCartLimit());
        assertEquals(192, profile.routeCap());
        assertEquals(Integer.MAX_VALUE, profile.remoteRange());
        assertEquals(12, profile.bridgeWidth());
        assertEquals(32, profile.supportDepth());
        assertTrue(profile.searchlight());
    }

    @Test void rosterHasTenFamiliesAndNineteenInstallableModules() {
        assertEquals(10, ModuleFamily.values().length);
        assertEquals(19, ModuleKind.values().length);
    }
}
