package com.bettercontent.railbeetle.item;

import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Fixed, authorable supplies for a found baseline Beetle. */
public final class StarterBeetlePackage {
    public static final String NBT_KEY = "RailBeetleStarterPackage";
    private static final int RAILS = 32;
    private static final int SUPPORTS = 16;
    private static final int FUEL = 8;

    private StarterBeetlePackage() {}

    public static boolean requested(ItemStack stack) {
        return requested(stack.getTag());
    }

    static boolean requested(CompoundTag tag) {
        return tag != null && tag.getBoolean(NBT_KEY);
    }

    public static void install(RailBeetleEntity beetle) {
        beetle.inventory().setStackInSlot(0, new ItemStack(Blocks.RAIL, RAILS));
        beetle.inventory().setStackInSlot(1, new ItemStack(Blocks.COBBLESTONE, SUPPORTS));
        beetle.inventory().setStackInSlot(2, new ItemStack(Items.COAL, FUEL));
    }
}
