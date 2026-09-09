package com.bettercontent.railbeetle;

import com.bettercontent.railbeetle.network.RailBeetleNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(RailBeetleMod.MOD_ID)
public final class RailBeetleMod {
    public static final String MOD_ID = "rail_beetle";

    public RailBeetleMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        RailBeetleRegistries.register(modBus);
        modBus.addListener(this::creativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RailBeetleConfig.SERVER_SPEC);
        RailBeetleNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(RailBeetleRegistries.RAIL_BEETLE_ITEM);
            event.accept(RailBeetleRegistries.ROUTE_BEACON_ITEM);
            event.accept(RailBeetleRegistries.ENGINE_CRADLE);
            event.accept(RailBeetleRegistries.COMPACT_MODULE_FRAME);
            event.accept(RailBeetleRegistries.REMOTE_CONTROL);
            RailBeetleRegistries.ENGINE_ITEMS.values().forEach(event::accept);
            RailBeetleRegistries.MODULE_ITEMS.values().forEach(event::accept);
        }
    }
}
