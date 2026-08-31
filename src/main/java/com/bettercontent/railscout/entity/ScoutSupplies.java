package com.bettercontent.railscout.entity;

import com.bettercontent.railscout.RailScoutTags;
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

public final class ScoutSupplies {
    private ScoutSupplies() {}

    public static int countRails(ItemStackHandler inventory) {
        return count(inventory, ScoutSupplies::isRail);
    }

    public static int countRails(ItemStackHandler inventory, RailShape shape) {
        return count(inventory, stack -> isRail(stack) && supportsRailShape(stack, shape));
    }

    public static int countSupports(ItemStackHandler inventory) {
        return count(inventory, ScoutSupplies::isSupport);
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
        return takeBlock(inventory, ScoutSupplies::isRail);
    }

    @Nullable
    public static BlockItem takeRail(ItemStackHandler inventory, RailShape shape) {
        return takeBlock(inventory, stack -> isRail(stack) && supportsRailShape(stack, shape));
    }

    @Nullable
    public static BlockItem takeSupport(ItemStackHandler inventory) {
        return takeBlock(inventory, ScoutSupplies::isSupport);
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
                || stack.is(RailScoutTags.FORGE_RAILS)
                || stack.is(RailScoutTags.USABLE_RAILS)
                || blockItem.getBlock().defaultBlockState().is(BlockTags.RAILS)
                || blockItem.getBlock().defaultBlockState().is(RailScoutTags.FORGE_RAIL_BLOCKS)
                || blockItem.getBlock().defaultBlockState().is(RailScoutTags.USABLE_RAIL_BLOCKS);
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
