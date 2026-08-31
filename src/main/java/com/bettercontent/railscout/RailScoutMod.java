package com.bettercontent.railscout;

import com.bettercontent.railscout.network.RailScoutNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(RailScoutMod.MOD_ID)
public final class RailScoutMod {
    public static final String MOD_ID = "rail_scout";

    public RailScoutMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        RailScoutRegistries.register(modBus);
        modBus.addListener(this::creativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RailScoutConfig.SERVER_SPEC);
        RailScoutNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(RailScoutRegistries.RAIL_SCOUT_ITEM);
            event.accept(RailScoutRegistries.ROUTE_BEACON_ITEM);
        }
    }
}
