package com.bettercontent.railscout;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RailScoutConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.IntValue ROUTE_RAIL_CAP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("routing");
        ROUTE_RAIL_CAP = builder
                .comment("Maximum number of new rails in each proposed route.")
                .defineInRange("routeRailCap", 64, 8, 256);
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private RailScoutConfig() {}
}
