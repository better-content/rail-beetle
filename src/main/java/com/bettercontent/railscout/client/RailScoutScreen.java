package com.bettercontent.railscout.client;

import com.bettercontent.railscout.entity.RailScoutEntity;
import com.bettercontent.railscout.menu.RailScoutMenu;
import com.bettercontent.railscout.network.RailScoutNetwork;
import com.bettercontent.railscout.network.ScoutControl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RailScoutScreen extends AbstractContainerScreen<RailScoutMenu> {
    private Button brakeButton;
    private final java.util.List<ControlButton> controlButtons = new java.util.ArrayList<>();

    public RailScoutScreen(RailScoutMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelY = 92;
    }

    @Override
    protected void init() {
        super.init();
        controlButtons.clear();
        int y = topPos + 74;
        addControl(leftPos + 7, y, 22, Component.translatable("screen.rail_scout.reverse.short"),
                "screen.rail_scout.reverse", ScoutControl.REVERSE);
        addControl(leftPos + 31, y, 22, Component.translatable("screen.rail_scout.stop.short"),
                "screen.rail_scout.stop", ScoutControl.STOP);
        addControl(leftPos + 55, y, 22, Component.translatable("screen.rail_scout.half.short"),
                "screen.rail_scout.half", ScoutControl.HALF_SPEED);
        addControl(leftPos + 79, y, 22, Component.translatable("screen.rail_scout.normal.short"),
                "screen.rail_scout.normal", ScoutControl.NORMAL_SPEED);
        addControl(leftPos + 103, y, 22, Component.translatable("screen.rail_scout.double.short"),
                "screen.rail_scout.double", ScoutControl.DOUBLE_SPEED);
        brakeButton = button(leftPos + 128, y, 41, brakeLabel(), ScoutControl.TOGGLE_HAND_BRAKE);
        brakeButton.setTooltip(Tooltip.create(Component.translatable("screen.rail_scout.brake.tooltip")));
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

    private void addControl(int x, int y, int width, Component label, String tooltipKey, ScoutControl action) {
        Button control = button(x, y, width, label, action);
        control.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        controlButtons.add(new ControlButton(control, action));
        addRenderableWidget(control);
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
        Component supplies = Component.translatable("screen.rail_scout.supplies",
                scout.railCount(), scout.supportCount(), scout.fuelTicks() / 20);
        graphics.drawString(font, supplies, imageWidth - 7 - font.width(supplies), titleLabelY, 0xbddce5, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xcbd2d6, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (ControlButton entry : controlButtons) {
            if (selected(entry.action())) {
                Button control = entry.button();
                graphics.fill(control.getX() + 3, control.getY() + control.getHeight() - 3,
                        control.getX() + control.getWidth() - 3, control.getY() + control.getHeight() - 1,
                        0xffffb33b);
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private boolean selected(ScoutControl control) {
        RailScoutEntity scout = menu.scout();
        return switch (control) {
            case REVERSE -> scout.mode().name().equals("MANUAL_REVERSE");
            case STOP -> !scout.mode().moves();
            case HALF_SPEED -> scout.mode().moves() && scout.speedTier().name().equals("HALF");
            case NORMAL_SPEED -> scout.mode().moves() && scout.speedTier().name().equals("NORMAL");
            case DOUBLE_SPEED -> scout.mode().moves() && scout.speedTier().name().equals("DOUBLE");
            default -> false;
        };
    }

    private record ControlButton(Button button, ScoutControl action) {}
}
