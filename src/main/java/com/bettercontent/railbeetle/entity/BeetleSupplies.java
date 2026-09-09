package com.bettercontent.railbeetle.entity;

import com.bettercontent.railbeetle.RailBeetleTags;
import com.bettercontent.railbeetle.navigation.RouteProposal;
import com.bettercontent.railbeetle.navigation.RouteSupplyStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public final class BeetleSupplies {
    private BeetleSupplies() {}

    public static int countRails(ItemStackHandler inventory) {
        return count(inventory, BeetleSupplies::isRail);
    }

    public static int countRails(ItemStackHandler inventory, RailShape shape) {
        return count(inventory, stack -> isRail(stack) && supportsRailShape(stack, shape));
    }

    public static int countSupports(ItemStackHandler inventory) {
        return count(inventory, BeetleSupplies::isSupport);
    }

    public static int totalFuelTicks(ItemStackHandler inventory) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            total += Math.max(0, ForgeHooks.getBurnTime(stack, RecipeType.SMELTING)) * stack.getCount();
        }
        return total;
    }

    @Nullable
    public static BlockItem takeRail(ItemStackHandler inventory) {
        return takeBlock(inventory, BeetleSupplies::isRail);
    }

    @Nullable
    public static BlockItem takeRail(ItemStackHandler inventory, RailShape shape) {
        return takeBlock(inventory, stack -> isRail(stack) && supportsRailShape(stack, shape));
    }

    @Nullable
    public static BlockItem takeSupport(ItemStackHandler inventory) {
        int bestSlot = -1;
        int bestBurnTime = Integer.MAX_VALUE;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!isSupport(stack)) continue;
            int burnTime = Math.max(0, ForgeHooks.getBurnTime(stack, RecipeType.SMELTING));
            if (burnTime < bestBurnTime) {
                bestBurnTime = burnTime;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return null;
        return (BlockItem) inventory.extractItem(bestSlot, 1, false).getItem();
    }

    public static RouteSupplyStatus supplyStatus(ItemStackHandler inventory, int bufferedFuelTicks,
                                                  RouteProposal route) {
        ItemStackHandler simulated = new ItemStackHandler(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            simulated.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        }
        boolean missingFuel = bufferedFuelTicks <= 0 && takeFuel(simulated) <= 0;
        int missingRails = 0;
        int missingSupports = 0;
        for (var step : route.steps()) {
            if (takeRail(simulated, step.shape()) == null) missingRails++;
            for (int support = 0; support < step.supportPositions().size(); support++) {
                if (takeSupport(simulated) == null) missingSupports++;
            }
        }
        return new RouteSupplyStatus(missingRails, missingSupports, missingFuel);
    }

    public static int takeFuel(ItemStackHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            int burnTime = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
            if (burnTime <= 0) continue;
            ItemStack consumed = inventory.extractItem(slot, 1, false);
            ItemStack remainder = consumed.getCraftingRemainingItem();
            if (!remainder.isEmpty()) {
                insertRemainder(inventory, remainder);
            }
            return burnTime;
        }
        return 0;
    }

    public static boolean isRail(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof BaseRailBlock)) {
            return false;
        }
        return stack.is(ItemTags.RAILS)
                || stack.is(RailBeetleTags.FORGE_RAILS)
                || stack.is(RailBeetleTags.USABLE_RAILS)
                || blockItem.getBlock().defaultBlockState().is(BlockTags.RAILS)
                || blockItem.getBlock().defaultBlockState().is(RailBeetleTags.FORGE_RAIL_BLOCKS)
                || blockItem.getBlock().defaultBlockState().is(RailBeetleTags.USABLE_RAIL_BLOCKS);
    }

    public static boolean supportsRailShape(ItemStack stack, RailShape shape) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof BaseRailBlock rail
                && supportsRailShape(rail.getShapeProperty(), shape);
    }

    public static boolean supportsRailShape(Property<RailShape> property, RailShape shape) {
        return property.getPossibleValues().contains(shape);
    }

    public static boolean isSupport(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock || block instanceof EntityBlock) return false;
        var state = block.defaultBlockState();
        return !state.isAir() && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static int count(ItemStackHandler inventory, java.util.function.Predicate<ItemStack> predicate) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (predicate.test(stack)) total += stack.getCount();
        }
        return total;
    }

    @Nullable
    private static BlockItem takeBlock(ItemStackHandler inventory, java.util.function.Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!predicate.test(stack)) continue;
            ItemStack extracted = inventory.extractItem(slot, 1, false);
            return (BlockItem) extracted.getItem();
        }
        return null;
    }

    private static void insertRemainder(ItemStackHandler inventory, ItemStack remainder) {
        ItemStack pending = remainder;
        for (int slot = 0; slot < inventory.getSlots() && !pending.isEmpty(); slot++) {
            pending = inventory.insertItem(slot, pending, false);
        }
    }
}
