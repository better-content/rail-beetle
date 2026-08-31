package com.bettercontent.railscout;

import com.bettercontent.railscout.entity.RailScoutEntity;
import com.bettercontent.railscout.block.RouteBeaconBlock;
import com.bettercontent.railscout.item.RailScoutItem;
import com.bettercontent.railscout.menu.RailScoutMenu;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RailScoutRegistries {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RailScoutMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RailScoutMod.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, RailScoutMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, RailScoutMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RailScoutMod.MOD_ID);

    public static final RegistryObject<EntityType<RailScoutEntity>> RAIL_SCOUT_ENTITY = ENTITIES.register(
            "rail_scout",
            () -> EntityType.Builder.<RailScoutEntity>of(RailScoutEntity::new, MobCategory.MISC)
                    .sized(0.98f, 0.7f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("rail_scout:rail_scout"));

    public static final RegistryObject<Item> RAIL_SCOUT_ITEM = ITEMS.register(
            "rail_scout", () -> new RailScoutItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> ROUTE_BEACON = BLOCKS.register(
            "route_beacon", () -> new RouteBeaconBlock(Block.Properties.copy(Blocks.OAK_FENCE).noOcclusion()));

    public static final RegistryObject<Item> ROUTE_BEACON_ITEM = ITEMS.register(
            "route_beacon", () -> new BlockItem(ROUTE_BEACON.get(), new Item.Properties()));

    public static final RegistryObject<MenuType<RailScoutMenu>> RAIL_SCOUT_MENU = MENUS.register(
            "rail_scout", () -> IForgeMenuType.create(RailScoutMenu::fromNetwork));

    public static final RegistryObject<SoundEvent> WHISTLE = SOUNDS.register(
            "whistle", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RailScoutMod.MOD_ID, "whistle")));

    private RailScoutRegistries() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        SOUNDS.register(bus);
    }
}
