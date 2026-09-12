package com.bettercontent.railbeetle.menu;

import com.bettercontent.railbeetle.RailBeetleRegistries;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import com.bettercontent.railbeetle.item.EngineItem;
import com.bettercontent.railbeetle.item.ModuleItem;

public final class RailBeetleMenu extends AbstractContainerMenu {
    private static final int CARGO_SLOTS = 27;
    private static final int MACHINE_SLOTS = 7;
    private final RailBeetleEntity beetle;

    public static RailBeetleMenu fromNetwork(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        Entity entity = playerInventory.player.level().getEntity(buffer.readVarInt());
        if (!(entity instanceof RailBeetleEntity beetle)) {
            throw new IllegalStateException("Rail Beetle menu opened for a missing entity");
        }
        return new RailBeetleMenu(id, playerInventory, beetle);
    }

    public RailBeetleMenu(int id, Inventory playerInventory, RailBeetleEntity beetle) {
        super(RailBeetleRegistries.RAIL_BEETLE_MENU.get(), id);
        this.beetle = beetle;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new SlotItemHandler(beetle.inventory(), column + row * 9, 8 + column * 18, 22 + row * 18));
            }
        }
        addSlot(new MachinerySlot(beetle.engineInventory(), 0, 216, 20));
        for (int slot = 0; slot < RailBeetleEntity.MODULE_SLOTS; slot++) {
            addSlot(new MachinerySlot(beetle.moduleInventory(), slot, 178 + (slot % 3) * 20, 52 + (slot / 3) * 20));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 138 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 196));
        }
    }

    public RailBeetleEntity beetle() {
        return beetle;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        int playerStart = CARGO_SLOTS + MACHINE_SLOTS;
        if (index < playerStart) {
            if (!moveItemStackTo(source, playerStart, slots.size(), true)) return ItemStack.EMPTY;
        } else if (source.getItem() instanceof EngineItem) {
            if (!moveItemStackTo(source, CARGO_SLOTS, CARGO_SLOTS + 1, false)) return ItemStack.EMPTY;
        } else if (source.getItem() instanceof ModuleItem) {
            if (!moveItemStackTo(source, CARGO_SLOTS + 1, playerStart, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, CARGO_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return beetle.isAlive() && player.distanceToSqr(beetle) <= 64.0;
    }

    private final class MachinerySlot extends SlotItemHandler {
        private MachinerySlot(net.minecraftforge.items.IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return beetle.canConfigureMachinery() && super.mayPlace(stack); }
        @Override public boolean mayPickup(Player player) { return beetle.canConfigureMachinery() && super.mayPickup(player); }
    }
}
