package com.bettercontent.railscout.menu;

import com.bettercontent.railscout.RailScoutRegistries;
import com.bettercontent.railscout.entity.RailScoutEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public final class RailScoutMenu extends AbstractContainerMenu {
    private static final int SCOUT_SLOTS = 27;
    private final RailScoutEntity scout;

    public static RailScoutMenu fromNetwork(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        Entity entity = playerInventory.player.level().getEntity(buffer.readVarInt());
        if (!(entity instanceof RailScoutEntity scout)) {
            throw new IllegalStateException("Rail Scout menu opened for a missing entity");
        }
        return new RailScoutMenu(id, playerInventory, scout);
    }

    public RailScoutMenu(int id, Inventory playerInventory, RailScoutEntity scout) {
        super(RailScoutRegistries.RAIL_SCOUT_MENU.get(), id);
        this.scout = scout;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new SlotItemHandler(scout.inventory(), column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 102 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 160));
        }
    }

    public RailScoutEntity scout() {
        return scout;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < SCOUT_SLOTS) {
            if (!moveItemStackTo(source, SCOUT_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, SCOUT_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return scout.isAlive() && player.distanceToSqr(scout) <= 64.0;
    }
}
