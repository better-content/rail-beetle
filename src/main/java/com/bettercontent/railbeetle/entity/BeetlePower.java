package com.bettercontent.railbeetle.entity;

import com.bettercontent.railbeetle.item.EngineItem;
import com.bettercontent.railbeetle.upgrade.BeetleProfile;
import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.bettercontent.railbeetle.upgrade.WorkAction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Optional;

final class BeetlePower {
    static final String CREDIT_TAG = "RailBeetleWorkCredit";
    static final String WATER_TAG = "RailBeetleWater";
    static final int STEAM_WATER_CAPACITY = 8_000;
    private BeetlePower() {}

    static int adjustedWork(WorkAction action, int rawWork, BeetleProfile profile, EngineKind engine) {
        if (rawWork <= 0) return 0;
        double work = Math.max(0, rawWork) * profile.efficiencyMultiplier();
        if (engine == EngineKind.SOURCE && action == WorkAction.SURVEY) work *= 0.70;
        if (engine == EngineKind.SOUL) work *= 0.70;
        if (engine == EngineKind.SPIRIT && (action == WorkAction.RAIL_PLACEMENT
                || action == WorkAction.SUPPORT_PLACEMENT || action == WorkAction.CLEARING)) work *= 0.70;
        return Math.max(1, (int) Math.ceil(work));
    }

    static boolean consumeEngine(ItemStack engineStack, ItemStackHandler cargo, int work) {
        if (work <= 0) return true;
        if (!(engineStack.getItem() instanceof EngineItem engineItem)) return false;
        EngineKind kind = engineItem.kind();
        int credit = Math.max(0, engineStack.getOrCreateTag().getInt(CREDIT_TAG));
        while (credit < work) {
            if (!refillEngine(engineStack, cargo)) break;
            int available = engineItem.resource(engineStack);
            if (available <= 0) break;
            int nativeDraw = switch (kind) {
                case FLUX -> Math.min(available / 256, work - credit) * 256;
                case PRESSURE -> Math.min(available / 5, work - credit) * 5;
                case SOUL -> Math.min(available, Math.max(1, (work - credit + 19) / 20));
                case SPIRIT -> Math.min(available, Math.max(1, (work - credit + 5_999) / 6_000));
                default -> Math.min(available, work - credit);
            };
            if (nativeDraw <= 0) break;
            engineItem.setResource(engineStack, available - nativeDraw);
            credit += switch (kind) {
                case FLUX -> nativeDraw / 256;
                case PRESSURE -> nativeDraw / 5;
                case SOUL -> nativeDraw * 20;
                case SPIRIT -> nativeDraw * 6_000;
                default -> nativeDraw;
            };
        }
        if (credit < work) return false;
        if (kind == EngineKind.STEAM && !consumeSteamWater(engineStack, cargo, work)) {
            engineStack.getOrCreateTag().putInt(CREDIT_TAG, credit);
            return false;
        }
        engineStack.getOrCreateTag().putInt(CREDIT_TAG, credit - work);
        return true;
    }

    static boolean refillEngine(ItemStack engineStack, ItemStackHandler cargo) {
        if (!(engineStack.getItem() instanceof EngineItem engineItem)) return false;
        EngineKind kind = engineItem.kind();
        int room = kind.capacity() - engineItem.resource(engineStack);
        if (room <= 0) return true;
        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            ItemStack stack = cargo.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            int accepted = switch (kind) {
                case STEAM -> refillBurnable(engineItem, engineStack, cargo, slot, stack, room);
                case FLUX -> refillEnergy(engineItem, engineStack, cargo, slot, stack, room);
                case SOURCE -> refillSimple(engineItem, engineStack, cargo, slot, stack, room,
                        "ars_nouveau", "source_gem", 1_000);
                case LIFEFORCE -> refillFluid(engineItem, engineStack, cargo, slot, stack, room,
                        "bloodmagic", "life_essence_fluid");
                case PRESSURE -> refillSimple(engineItem, engineStack, cargo, slot, stack, room,
                        "pneumaticcraft", "air_canister", 72_000);
                case SPIRIT -> refillSpirit(engineItem, engineStack, cargo, slot, stack, room);
                default -> 0;
            };
            if (accepted > 0) return true;
        }
        return engineItem.resource(engineStack) > 0;
    }

    private static int refillBurnable(EngineItem item, ItemStack engine, ItemStackHandler cargo,
                                      int slot, ItemStack stack, int room) {
        int burn = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
        if (burn <= 0) return 0;
        ItemStack consumed = cargo.extractItem(slot, 1, false);
        ItemStack remainder = consumed.getCraftingRemainingItem();
        if (!remainder.isEmpty()) insert(cargo, remainder);
        int accepted = Math.min(room, burn);
        item.setResource(engine, item.resource(engine) + accepted);
        return accepted;
    }

    private static int refillEnergy(EngineItem item, ItemStack engine, ItemStackHandler cargo,
                                    int slot, ItemStack stack, int room) {
        Optional<IEnergyStorage> storage = stack.getCapability(ForgeCapabilities.ENERGY).resolve();
        if (storage.isEmpty()) return 0;
        int moved = storage.get().extractEnergy(room, false);
        if (moved <= 0) return 0;
        cargo.setStackInSlot(slot, stack);
        item.setResource(engine, item.resource(engine) + moved);
        return moved;
    }

    private static int refillFluid(EngineItem item, ItemStack engine, ItemStackHandler cargo,
                                   int slot, ItemStack stack, int room, String namespace, String path) {
        ItemStack one = cargo.extractItem(slot, 1, false);
        Optional<IFluidHandlerItem> handler = one.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve();
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (handler.isEmpty() || fluid == null) {
            insert(cargo, one);
            return 0;
        }
        FluidStack drained = handler.get().drain(new FluidStack(fluid, room), IFluidHandler.FluidAction.EXECUTE);
        insert(cargo, handler.get().getContainer());
        if (drained.isEmpty()) return 0;
        item.setResource(engine, item.resource(engine) + drained.getAmount());
        return drained.getAmount();
    }

    private static int refillSimple(EngineItem item, ItemStack engine, ItemStackHandler cargo,
                                    int slot, ItemStack stack, int room,
                                    String namespace, String path, int value) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.equals(ResourceLocation.fromNamespaceAndPath(namespace, path))) return 0;
        ItemStack consumed = cargo.extractItem(slot, 1, false);
        if (!consumed.getCraftingRemainingItem().isEmpty()) insert(cargo, consumed.getCraftingRemainingItem());
        int accepted = Math.min(room, value);
        item.setResource(engine, item.resource(engine) + accepted);
        return accepted;
    }

    private static int refillSpirit(EngineItem item, ItemStack engine, ItemStackHandler cargo,
                                    int slot, ItemStack stack, int room) {
        if (!stack.is(MalumTags.SPIRITS)) return 0;
        cargo.extractItem(slot, 1, false);
        item.setResource(engine, item.resource(engine) + 1);
        return 1;
    }

    private static boolean consumeSteamWater(ItemStack engine, ItemStackHandler cargo, int work) {
        int debt = Math.max(0, engine.getOrCreateTag().getInt("RailBeetleSteamDebt")) + work;
        int waterCost = debt / 20;
        debt %= 20;
        int water = Math.max(0, engine.getOrCreateTag().getInt(WATER_TAG));
        while (water < waterCost && refillSteamWater(engine, cargo)) water = engine.getOrCreateTag().getInt(WATER_TAG);
        if (water < waterCost) return false;
        engine.getOrCreateTag().putInt(WATER_TAG, water - waterCost);
        engine.getOrCreateTag().putInt("RailBeetleSteamDebt", debt);
        return true;
    }

    private static boolean refillSteamWater(ItemStack engine, ItemStackHandler cargo) {
        int water = Math.max(0, engine.getOrCreateTag().getInt(WATER_TAG));
        int room = STEAM_WATER_CAPACITY - water;
        if (room <= 0) return true;
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("minecraft", "water"));
        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            ItemStack one = cargo.extractItem(slot, 1, false);
            if (one.isEmpty()) continue;
            Optional<IFluidHandlerItem> handler = one.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve();
            if (handler.isPresent()) {
                FluidStack drained = handler.get().drain(new FluidStack(fluid, room), IFluidHandler.FluidAction.EXECUTE);
                insert(cargo, handler.get().getContainer());
                if (!drained.isEmpty()) {
                    engine.getOrCreateTag().putInt(WATER_TAG, water + drained.getAmount());
                    return true;
                }
            } else insert(cargo, one);
        }
        return false;
    }

    private static void insert(ItemStackHandler cargo, ItemStack stack) {
        ItemStack pending = stack;
        for (int slot = 0; slot < cargo.getSlots() && !pending.isEmpty(); slot++) {
            pending = cargo.insertItem(slot, pending, false);
        }
    }

    private static final class MalumTags {
        private static final TagKey<Item> SPIRITS = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("rail_beetle", "malum_spirits"));
    }
}
