package com.bettercontent.railbeetle;

import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import com.bettercontent.railbeetle.block.RouteBeaconBlock;
import com.bettercontent.railbeetle.item.RailBeetleItem;
import com.bettercontent.railbeetle.item.EngineItem;
import com.bettercontent.railbeetle.item.ModuleItem;
import com.bettercontent.railbeetle.item.RemoteControlItem;
import com.bettercontent.railbeetle.menu.RailBeetleMenu;
import com.bettercontent.railbeetle.menu.RemoteBeetleMenu;
import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.bettercontent.railbeetle.upgrade.ModuleKind;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class RailBeetleRegistries {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RailBeetleMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RailBeetleMod.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, RailBeetleMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, RailBeetleMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RailBeetleMod.MOD_ID);

    public static final RegistryObject<EntityType<RailBeetleEntity>> RAIL_BEETLE_ENTITY = ENTITIES.register(
            "rail_beetle",
            () -> EntityType.Builder.<RailBeetleEntity>of(RailBeetleEntity::new, MobCategory.MISC)
                    .sized(0.98f, 0.7f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("rail_beetle:rail_beetle"));

    public static final RegistryObject<Item> RAIL_BEETLE_ITEM = ITEMS.register(
            "rail_beetle", () -> new RailBeetleItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> ROUTE_BEACON = BLOCKS.register(
            "route_beacon", () -> new RouteBeaconBlock(Block.Properties.copy(Blocks.OAK_FENCE).noOcclusion()));

    public static final RegistryObject<Item> ROUTE_BEACON_ITEM = ITEMS.register(
            "route_beacon", () -> new BlockItem(ROUTE_BEACON.get(), new Item.Properties()));

    public static final RegistryObject<Item> ENGINE_CRADLE = ITEMS.register(
            "engine_cradle", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COMPACT_MODULE_FRAME = ITEMS.register(
            "compact_module_frame", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> REMOTE_CONTROL = ITEMS.register(
            "dispatch_remote", () -> new RemoteControlItem(new Item.Properties().stacksTo(1)));

    private static final Map<EngineKind, RegistryObject<Item>> MUTABLE_ENGINE_ITEMS = new EnumMap<>(EngineKind.class);
    private static final Map<ModuleKind, RegistryObject<Item>> MUTABLE_MODULE_ITEMS = new EnumMap<>(ModuleKind.class);
    public static final Map<EngineKind, RegistryObject<Item>> ENGINE_ITEMS;
    public static final Map<ModuleKind, RegistryObject<Item>> MODULE_ITEMS;

    static {
        for (EngineKind kind : EngineKind.values()) {
            if (kind.builtIn() || (!kind.requiredMod().isEmpty() && !ModList.get().isLoaded(kind.requiredMod()))) continue;
            MUTABLE_ENGINE_ITEMS.put(kind, ITEMS.register(kind.id(),
                    () -> new EngineItem(kind, new Item.Properties().stacksTo(1))));
        }
        for (ModuleKind kind : ModuleKind.values()) {
            MUTABLE_MODULE_ITEMS.put(kind, ITEMS.register(kind.id(),
                    () -> new ModuleItem(kind, new Item.Properties().stacksTo(1))));
        }
        ENGINE_ITEMS = Collections.unmodifiableMap(MUTABLE_ENGINE_ITEMS);
        MODULE_ITEMS = Collections.unmodifiableMap(MUTABLE_MODULE_ITEMS);
    }

    public static final RegistryObject<MenuType<RailBeetleMenu>> RAIL_BEETLE_MENU = MENUS.register(
            "rail_beetle", () -> IForgeMenuType.create(RailBeetleMenu::fromNetwork));
    public static final RegistryObject<MenuType<RemoteBeetleMenu>> REMOTE_BEETLE_MENU = MENUS.register(
            "remote_beetle", () -> IForgeMenuType.create(RemoteBeetleMenu::fromNetwork));

    public static final RegistryObject<SoundEvent> WHISTLE = SOUNDS.register(
            "whistle", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RailBeetleMod.MOD_ID, "whistle")));

    private RailBeetleRegistries() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        SOUNDS.register(bus);
    }
}
