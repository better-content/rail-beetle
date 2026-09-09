package com.bettercontent.railbeetle.entity;

import com.bettercontent.railbeetle.upgrade.BeetleProfile;
import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.bettercontent.railbeetle.upgrade.ModuleKind;
import com.bettercontent.railbeetle.upgrade.WorkAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BeetlePowerTest {
    @Test void zeroWorkIsActuallyFree() {
        assertEquals(0, BeetlePower.adjustedWork(WorkAction.SEARCHLIGHT, 0,
                BeetleProfile.from(List.of()), EngineKind.FIREBOX));
    }

    @Test void efficiencyAndEngineSpecialtiesStack() {
        BeetleProfile efficient = BeetleProfile.from(List.of(ModuleKind.EXHAUST_RECUPERATOR_II));
        assertEquals(28, BeetlePower.adjustedWork(WorkAction.SURVEY, 100, efficient, EngineKind.SOURCE));
        assertEquals(28, BeetlePower.adjustedWork(WorkAction.CLEARING, 100, efficient, EngineKind.SPIRIT));
        assertEquals(28, BeetlePower.adjustedWork(WorkAction.MOTION, 100, efficient, EngineKind.SOUL));
    }

    @Test void nativeUnitCostsMatchThePowerContract() {
        assertEquals(256, EngineKind.FLUX.nativeCost(1));
        assertEquals(5, EngineKind.PRESSURE.nativeCost(1));
        assertEquals(1, EngineKind.SOUL.nativeCost(20));
        assertEquals(1, EngineKind.SPIRIT.nativeCost(6_000));
    }
}
