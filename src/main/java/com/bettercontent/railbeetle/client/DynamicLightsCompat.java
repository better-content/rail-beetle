package com.bettercontent.railbeetle.client;

import com.bettercontent.railbeetle.RailBeetleRegistries;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import dev.lambdaurora.lambdynlights.api.DynamicLightHandler;
import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;

final class DynamicLightsCompat {
    private DynamicLightsCompat() {}

    static void register() {
        DynamicLightHandlers.registerDynamicLightHandler(RailBeetleRegistries.RAIL_BEETLE_ENTITY.get(),
                new DynamicLightHandler<RailBeetleEntity>() {
                    @Override public int getLuminance(RailBeetleEntity beetle) {
                        return beetle.searchlightOn() ? 15 : 0;
                    }
                    @Override public boolean isWaterSensitive(RailBeetleEntity beetle) { return true; }
                });
    }
}
