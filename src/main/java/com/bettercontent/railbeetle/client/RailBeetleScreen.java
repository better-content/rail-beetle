package com.bettercontent.railbeetle.client;

import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import com.bettercontent.railbeetle.menu.RailBeetleMenu;
import com.bettercontent.railbeetle.network.RailBeetleNetwork;
import com.bettercontent.railbeetle.network.BeetleControl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RailBeetleScreen extends AbstractContainerScreen<RailBeetleMenu> {
    private Button brakeButton;
    private Button neutralButton;
    private Button doubleButton;
    private Button tripleButton;
    private Button lightButton;
    private final java.util.List<ControlButton> controlButtons = new java.util.ArrayList<>();

    public RailBeetleScreen(RailBeetleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 280;
        imageHeight = 184;
        inventoryLabelY = 92;
    }

    @Override
    protected void init() {
        super.init();
        controlButtons.clear();
        int y = topPos + 74;
        addControl(leftPos + 7, y, 22, Component.translatable("screen.rail_beetle.reverse.short"),
                "screen.rail_beetle.reverse", BeetleControl.REVERSE);
        addControl(leftPos + 31, y, 22, Component.translatable("screen.rail_beetle.stop.short"),
                "screen.rail_beetle.stop", BeetleControl.STOP);
        addControl(leftPos + 55, y, 22, Component.translatable("screen.rail_beetle.half.short"),
                "screen.rail_beetle.half", BeetleControl.HALF_SPEED);
        addControl(leftPos + 79, y, 22, Component.translatable("screen.rail_beetle.normal.short"),
                "screen.rail_beetle.normal", BeetleControl.NORMAL_SPEED);
        addControl(leftPos + 103, y, 22, Component.translatable("screen.rail_beetle.double.short"),
                "screen.rail_beetle.double", BeetleControl.DOUBLE_SPEED);
        addControl(leftPos + 127, y, 22, Component.translatable("screen.rail_beetle.triple.short"),
                "screen.rail_beetle.triple", BeetleControl.TRIPLE_SPEED);
        doubleButton = controlButtons.get(controlButtons.size() - 2).button();
        tripleButton = controlButtons.get(controlButtons.size() - 1).button();
        neutralButton = button(leftPos + 244, topPos + 18, 29, neutralLabel(), BeetleControl.TOGGLE_NEUTRAL);
        neutralButton.setTooltip(Tooltip.create(Component.translatable("screen.rail_beetle.neutral.tooltip")));
        addRenderableWidget(neutralButton);
        brakeButton = button(leftPos + 244, topPos + 42, 29, brakeLabel(), BeetleControl.TOGGLE_HAND_BRAKE);
        brakeButton.setTooltip(Tooltip.create(Component.translatable("screen.rail_beetle.brake.tooltip")));
        addRenderableWidget(brakeButton);
        lightButton = button(leftPos + 151, y, 20, Component.translatable("screen.rail_beetle.searchlight.short"),
                BeetleControl.TOGGLE_SEARCHLIGHT);
        lightButton.setTooltip(Tooltip.create(Component.translatable("screen.rail_beetle.searchlight")));
        addRenderableWidget(lightButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        brakeButton.setMessage(brakeLabel());
        neutralButton.setMessage(neutralLabel());
        doubleButton.active = menu.beetle().profile().governorTier() >= 1;
        tripleButton.active = menu.beetle().profile().governorTier() >= 2;
        lightButton.active = menu.beetle().profile().searchlight();
        lightButton.setMessage(Component.translatable(menu.beetle().searchlightOn()
                ? "screen.rail_beetle.searchlight.on" : "screen.rail_beetle.searchlight.off"));
    }

    private Component brakeLabel() {
        RailBeetleEntity beetle = menu.beetle();
        return Component.translatable(beetle.forcedBrake()
                ? "screen.rail_beetle.brake.locked"
                : beetle.brakeApplied() ? "screen.rail_beetle.brake.auto_on" : "screen.rail_beetle.brake.auto_off");
    }

    private Component neutralLabel() {
        return Component.translatable(menu.beetle().neutral()
                ? "screen.rail_beetle.neutral.on"
                : "screen.rail_beetle.neutral.off");
    }

    private Button button(int x, int y, int width, Component label, BeetleControl action) {
        return Button.builder(label, ignored -> RailBeetleNetwork.control(menu.beetle().getId(), action))
                .bounds(x, y, width, 18).build();
    }

    private void addControl(int x, int y, int width, Component label, String tooltipKey, BeetleControl action) {
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
        graphics.fill(leftPos + 174, topPos + 15, leftPos + 239, topPos + 73, 0xff252b30);
        graphics.fill(leftPos + 241, topPos + 15, leftPos + 276, topPos + 73, 0xff252b30);
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            var value = menu.slots.get(slot);
            graphics.fill(leftPos + value.x - 1, topPos + value.y - 1,
                    leftPos + value.x + 17, topPos + value.y + 17, 0xff3b434a);
        }
        RailBeetleEntity beetle = menu.beetle();
        int capacity = Math.max(1, beetle.engineKind().builtIn() ? 1_600 : beetle.engineKind().capacity());
        int fill = Math.min(56, (int) (56L * beetle.engineResource() / capacity));
        graphics.fill(leftPos + 178, topPos + 67, leftPos + 236, topPos + 71, 0xff0b0e10);
        graphics.fill(leftPos + 179, topPos + 68, leftPos + 179 + fill, topPos + 70, 0xffd39b39);
        if (!beetle.canConfigureMachinery()) {
            graphics.fill(leftPos + 174, topPos + 15, leftPos + 239, topPos + 73, 0x99301818);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RailBeetleEntity beetle = menu.beetle();
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xe8f5f8, false);
        Component supplies = beetle.engineKind().builtIn()
                ? Component.translatable("screen.rail_beetle.supplies", beetle.railCount(), beetle.supportCount(),
                    beetle.engineResource(), beetle.engineKind().unit())
                : Component.translatable("screen.rail_beetle.supplies.alt", beetle.railCount(), beetle.supportCount(),
                    beetle.engineResource(), beetle.engineKind().unit(), beetle.fallbackFuel());
        graphics.drawString(font, supplies, 169 - font.width(supplies), titleLabelY, 0xbddce5, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.engine"), 177, 6, 0xffd6aa5b, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.modules"), 177, 31, 0xffc6a96e, false);
        if (!beetle.canConfigureMachinery()) {
            Component locked = Component.translatable("screen.rail_beetle.machinery_locked");
            graphics.drawWordWrap(font, locked, 177, 17, 59, 0xffffb0a5);
        }
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

    private boolean selected(BeetleControl control) {
        RailBeetleEntity beetle = menu.beetle();
        return switch (control) {
            case REVERSE -> beetle.mode().name().equals("MANUAL_REVERSE");
            case STOP -> !beetle.mode().moves();
            case HALF_SPEED -> beetle.mode().moves() && beetle.speedTier().name().equals("HALF");
            case NORMAL_SPEED -> beetle.mode().moves() && beetle.speedTier().name().equals("NORMAL");
            case DOUBLE_SPEED -> beetle.mode().moves() && beetle.speedTier().name().equals("DOUBLE");
            case TRIPLE_SPEED -> beetle.mode().moves() && beetle.speedTier().name().equals("TRIPLE");
            case TOGGLE_SEARCHLIGHT -> beetle.searchlightOn();
            default -> false;
        };
    }

    private record ControlButton(Button button, BeetleControl action) {}
}
