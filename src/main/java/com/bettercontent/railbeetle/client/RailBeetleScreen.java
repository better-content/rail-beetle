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
        imageWidth = 344;
        imageHeight = 246;
        inventoryLabelY = 126;
    }

    @Override
    protected void init() {
        super.init();
        controlButtons.clear();
        addControl(leftPos + 250, topPos + 22, 86, Component.translatable("screen.rail_beetle.stop.button"),
                "screen.rail_beetle.stop", BeetleControl.STOP);
        addControl(leftPos + 250, topPos + 43, 86, Component.translatable("screen.rail_beetle.reverse.button"),
                "screen.rail_beetle.reverse", BeetleControl.REVERSE);
        addControl(leftPos + 250, topPos + 79, 41, Component.translatable("screen.rail_beetle.half.button"),
                "screen.rail_beetle.half", BeetleControl.HALF_SPEED);
        addControl(leftPos + 295, topPos + 79, 41, Component.translatable("screen.rail_beetle.normal.button"),
                "screen.rail_beetle.normal", BeetleControl.NORMAL_SPEED);
        addControl(leftPos + 250, topPos + 100, 41, Component.translatable("screen.rail_beetle.double.button"),
                "screen.rail_beetle.double", BeetleControl.DOUBLE_SPEED);
        addControl(leftPos + 295, topPos + 100, 41, Component.translatable("screen.rail_beetle.triple.button"),
                "screen.rail_beetle.triple", BeetleControl.TRIPLE_SPEED);
        doubleButton = controlButtons.get(controlButtons.size() - 2).button();
        tripleButton = controlButtons.get(controlButtons.size() - 1).button();
        lightButton = button(leftPos + 250, topPos + 147, 86, lightLabel(), BeetleControl.TOGGLE_SEARCHLIGHT);
        lightButton.setTooltip(Tooltip.create(Component.translatable("screen.rail_beetle.searchlight")));
        addRenderableWidget(lightButton);
        neutralButton = button(leftPos + 250, topPos + 168, 86, neutralLabel(), BeetleControl.TOGGLE_NEUTRAL);
        neutralButton.setTooltip(Tooltip.create(Component.translatable("screen.rail_beetle.neutral.tooltip")));
        addRenderableWidget(neutralButton);
        brakeButton = button(leftPos + 250, topPos + 189, 86, brakeLabel(), BeetleControl.TOGGLE_HAND_BRAKE);
        brakeButton.setTooltip(Tooltip.create(Component.translatable("screen.rail_beetle.brake.tooltip")));
        addRenderableWidget(brakeButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        brakeButton.setMessage(brakeLabel());
        neutralButton.setMessage(neutralLabel());
        doubleButton.active = menu.beetle().profile().governorTier() >= 1;
        tripleButton.active = menu.beetle().profile().governorTier() >= 2;
        lightButton.active = menu.beetle().profile().searchlight();
        lightButton.setMessage(lightLabel());
    }

    private Component brakeLabel() {
        RailBeetleEntity beetle = menu.beetle();
        return Component.translatable(beetle.forcedBrake()
                ? "screen.rail_beetle.brake.on"
                : "screen.rail_beetle.brake.auto");
    }

    private Component neutralLabel() {
        return Component.translatable(menu.beetle().neutral()
                ? "screen.rail_beetle.neutral.on"
                : "screen.rail_beetle.neutral.off");
    }

    private Component lightLabel() {
        return Component.translatable(menu.beetle().searchlightOn()
                ? "screen.rail_beetle.searchlight.on" : "screen.rail_beetle.searchlight.off");
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
        graphics.fill(leftPos + 5, topPos + 16, leftPos + 171, topPos + 78, 0xff252b30);
        graphics.fill(leftPos + 5, topPos + 132, leftPos + 171, topPos + 241, 0xff252b30);
        graphics.fill(leftPos + 174, topPos + 16, leftPos + 242, topPos + 94, 0xff252b30);
        graphics.fill(leftPos + 174, topPos + 98, leftPos + 242, topPos + 241, 0xff252b30);
        graphics.fill(leftPos + 246, topPos + 16, leftPos + 340, topPos + 241, 0xff252b30);
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            var value = menu.slots.get(slot);
            graphics.fill(leftPos + value.x - 1, topPos + value.y - 1,
                    leftPos + value.x + 17, topPos + value.y + 17, 0xff59636a);
            graphics.fill(leftPos + value.x, topPos + value.y,
                    leftPos + value.x + 16, topPos + value.y + 16, 0xff30373c);
        }
        RailBeetleEntity beetle = menu.beetle();
        int capacity = Math.max(1, beetle.engineKind().builtIn() ? 1_600 : beetle.engineKind().capacity());
        int fill = Math.min(56, (int) (56L * beetle.engineResource() / capacity));
        graphics.fill(leftPos + 178, topPos + 89, leftPos + 238, topPos + 93, 0xff0b0e10);
        graphics.fill(leftPos + 179, topPos + 90, leftPos + 179 + fill, topPos + 92, 0xffd39b39);
        if (!beetle.canConfigureMachinery()) {
            graphics.fill(leftPos + 174, topPos + 16, leftPos + 242, topPos + 94, 0x99301818);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RailBeetleEntity beetle = menu.beetle();
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xe8f5f8, false);
        Component cargo = Component.translatable("screen.rail_beetle.cargo");
        graphics.drawString(font, cargo, 169 - font.width(cargo), titleLabelY, 0xffc6a96e, false);
        Component supplies = Component.translatable("screen.rail_beetle.supplies",
                beetle.railCount(), beetle.supportCount());
        graphics.drawString(font, supplies, 8, 82, 0xbddce5, false);
        Component power = Component.translatable("screen.rail_beetle.power",
                com.bettercontent.railbeetle.upgrade.EngineKind.compactAmount(beetle.engineResource()),
                beetle.engineKind().unit());
        graphics.drawString(font, power, 8, 93, 0xbddce5, false);
        if (!beetle.engineKind().builtIn()) {
            graphics.drawString(font, Component.translatable("screen.rail_beetle.fallback", beetle.fallbackFuel()),
                    8, 104, 0xff9faeb5, false);
        }
        boolean configurable = beetle.canConfigureMachinery();
        graphics.drawString(font, Component.translatable("screen.rail_beetle.machinery"), 177, 6, 0xffd6aa5b, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.engine"), 177, 24, 0xffc6a96e, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.modules"), 177, 42, 0xffc6a96e, false);
        graphics.drawString(font, Component.translatable(configurable
                ? "screen.rail_beetle.machinery_ready" : "screen.rail_beetle.machinery_locked"),
                178, 101, configurable ? 0xff8fdb9b : 0xffff8f84, false);
        graphics.drawString(font, Component.translatable(configurable
                ? "screen.rail_beetle.machinery_ready.help.one" : "screen.rail_beetle.machinery_locked.help.one"),
                178, 112, 0xffcbd2d6, false);
        graphics.drawString(font, Component.translatable(configurable
                ? "screen.rail_beetle.machinery_ready.help.two" : "screen.rail_beetle.machinery_locked.help.two"),
                178, 123, 0xffcbd2d6, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.controls"), 250, 6, 0xffd6aa5b, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.forward_speed"), 250, 68, 0xffc6a96e, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.governor_hint.one"), 250, 122, 0xff9faeb5, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.governor_hint.two"), 250, 132, 0xff9faeb5, false);
        graphics.drawString(font, Component.translatable("screen.rail_beetle.current_mode"), 250, 212, 0xffc6a96e, false);
        var modeLines = font.split(Component.translatable("mode.rail_beetle." +
                beetle.mode().name().toLowerCase(java.util.Locale.ROOT)), 86);
        for (int line = 0; line < Math.min(2, modeLines.size()); line++) {
            graphics.drawString(font, modeLines.get(line), 250, 223 + line * 10, 0xffe8f5f8, false);
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
            case STOP -> !beetle.mode().moves() && !beetle.neutral();
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
