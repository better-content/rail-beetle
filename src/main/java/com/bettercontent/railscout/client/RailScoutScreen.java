package com.bettercontent.railscout.client;

import com.bettercontent.railscout.entity.RailScoutEntity;
import com.bettercontent.railscout.menu.RailScoutMenu;
import com.bettercontent.railscout.network.RailScoutNetwork;
import com.bettercontent.railscout.network.ScoutControl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RailScoutScreen extends AbstractContainerScreen<RailScoutMenu> {
    private Button brakeButton;

    public RailScoutScreen(RailScoutMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelY = 90;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + 75;
        addRenderableWidget(button(leftPos + 7, y, 38, Component.translatable("screen.rail_scout.stop"), ScoutControl.STOP));
        addRenderableWidget(button(leftPos + 48, y, 38, Component.translatable("screen.rail_scout.forward"), ScoutControl.FORWARD));
        addRenderableWidget(button(leftPos + 89, y, 38, Component.translatable("screen.rail_scout.reverse"), ScoutControl.SLOW_REVERSE));
        brakeButton = button(leftPos + 130, y, 39, brakeLabel(), ScoutControl.TOGGLE_HAND_BRAKE);
        addRenderableWidget(brakeButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        brakeButton.setMessage(brakeLabel());
    }

    private Component brakeLabel() {
        return Component.translatable(menu.scout().forcedBrake()
                ? "screen.rail_scout.brake.locked"
                : "screen.rail_scout.brake.auto");
    }

    private Button button(int x, int y, int width, Component label, ScoutControl action) {
        return Button.builder(label, ignored -> RailScoutNetwork.control(menu.scout().getId(), action))
                .bounds(x, y, width, 18).build();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xee161a1d);
        graphics.fill(leftPos + 5, topPos + 15, leftPos + 171, topPos + 73, 0xff252b30);
        graphics.fill(leftPos + 5, topPos + 99, leftPos + 171, topPos + 179, 0xff252b30);
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            var value = menu.slots.get(slot);
            graphics.fill(leftPos + value.x - 1, topPos + value.y - 1,
                    leftPos + value.x + 17, topPos + value.y + 17, 0xff3b434a);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RailScoutEntity scout = menu.scout();
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xe8f5f8, false);
        Component status = Component.translatable("screen.rail_scout.status",
                Component.translatable("mode.rail_scout." + scout.mode().name().toLowerCase()),
                scout.railCount(), scout.supportCount(), scout.fuelTicks());
        graphics.drawString(font, status, 8, 65, 0xbddce5, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xcbd2d6, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
