package com.bettercontent.railbeetle.upgrade;

import java.util.EnumMap;
import java.util.Map;

public record BeetleProfile(
        int governorTier,
        int recuperatorTier,
        int brakeTier,
        int adhesionTier,
        int torqueTier,
        int drawgearTier,
        int surveyTier,
        int remoteTier,
        int trestleTier,
        boolean searchlight
) {
    public static BeetleProfile from(Iterable<ModuleKind> modules) {
        Map<ModuleFamily, Integer> tiers = new EnumMap<>(ModuleFamily.class);
        for (ModuleKind module : modules) tiers.merge(module.family(), module.tier(), Math::max);
        return new BeetleProfile(
                tiers.getOrDefault(ModuleFamily.GOVERNOR, 0),
                tiers.getOrDefault(ModuleFamily.RECUPERATOR, 0),
                tiers.getOrDefault(ModuleFamily.BRAKES, 0),
                tiers.getOrDefault(ModuleFamily.ADHESION, 0),
                tiers.getOrDefault(ModuleFamily.TORQUE, 0),
                tiers.getOrDefault(ModuleFamily.DRAWGEAR, 0),
                tiers.getOrDefault(ModuleFamily.SURVEY, 0),
                tiers.getOrDefault(ModuleFamily.REMOTE, 0),
                tiers.getOrDefault(ModuleFamily.TRESTLE, 0),
                tiers.getOrDefault(ModuleFamily.SEARCHLIGHT, 0) > 0);
    }

    public int routeCap() { return switch (surveyTier) { case 1 -> 128; case 2 -> 192; default -> 64; }; }
    public double maxBlocksPerSecond() { return switch (governorTier) { case 1 -> 8.0; case 2 -> 12.0; default -> 4.0; }; }
    public double efficiencyMultiplier() { return switch (recuperatorTier) { case 1 -> 0.70; case 2 -> 0.40; default -> 1.0; }; }
    public double serviceDeceleration() { return switch (brakeTier) { case 1 -> 0.04; case 2 -> 0.08; default -> 0.02; }; }
    public double torqueMultiplier() { return switch (torqueTier) { case 1 -> 1.75; case 2 -> 3.0; default -> 1.0; }; }
    public int trailingCartLimit() { return switch (drawgearTier) { case 1 -> 6; case 2 -> 12; default -> 2; }; }
    public int bridgeWidth() { return switch (trestleTier) { case 1 -> 5; case 2 -> 12; default -> 1; }; }
    public int supportDepth() { return switch (trestleTier) { case 1 -> 12; case 2 -> 32; default -> 4; }; }
    public int remoteRange() { return remoteTier == 1 ? 128 : remoteTier == 2 ? Integer.MAX_VALUE : 0; }
    public double uphillSpeedMultiplier(boolean solo) {
        if (solo) return 1.0;
        return switch (adhesionTier) { case 1 -> 0.75; case 2 -> 1.0; default -> 0.5; };
    }
}
