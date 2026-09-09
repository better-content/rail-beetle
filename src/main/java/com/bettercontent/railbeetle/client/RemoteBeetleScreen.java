package com.bettercontent.railbeetle.client;

import com.bettercontent.railbeetle.menu.RemoteBeetleMenu;
import com.bettercontent.railbeetle.network.BeetleControl;
import com.bettercontent.railbeetle.network.RailBeetleNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RemoteBeetleScreen extends AbstractContainerScreen<RemoteBeetleMenu> {
    private final java.util.Map<BeetleControl, Button> controls = new java.util.EnumMap<>(BeetleControl.class);
    public RemoteBeetleScreen(RemoteBeetleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 220;
        imageHeight = 104;
    }

    @Override protected void init() {
        super.init();
        controls.clear();
        int y = topPos + 53;
        addControl(leftPos + 8, y, 27, "R", "screen.rail_beetle.reverse", BeetleControl.REVERSE);
        addControl(leftPos + 38, y, 27, "■", "screen.rail_beetle.stop", BeetleControl.STOP);
        addControl(leftPos + 68, y, 27, ".5×", "screen.rail_beetle.half", BeetleControl.HALF_SPEED);
        addControl(leftPos + 98, y, 27, "1×", "screen.rail_beetle.normal", BeetleControl.NORMAL_SPEED);
        addControl(leftPos + 128, y, 27, "2×", "screen.rail_beetle.double", BeetleControl.DOUBLE_SPEED);
        addControl(leftPos + 158, y, 27, "3×", "screen.rail_beetle.triple", BeetleControl.TRIPLE_SPEED);
        addControl(leftPos + 188, y, 24, "☼", "screen.rail_beetle.searchlight", BeetleControl.TOGGLE_SEARCHLIGHT);
    }

    private void addControl(int x, int y, int width, String label, String tooltip, BeetleControl action) {
        Button button = Button.builder(Component.literal(label), ignored -> RailBeetleNetwork.control(menu.entityId(), action))
                .bounds(x, y, width, 18).tooltip(Tooltip.create(Component.translatable(tooltip))).build();
        controls.put(action, button);
        addRenderableWidget(button);
    }

    @Override protected void containerTick() {
        super.containerTick();
        controls.get(BeetleControl.DOUBLE_SPEED).active = menu.profile().governorTier() >= 1;
        controls.get(BeetleControl.TRIPLE_SPEED).active = menu.profile().governorTier() >= 2;
        controls.get(BeetleControl.TOGGLE_SEARCHLIGHT).active = menu.profile().searchlight();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xf0161a1d);
        graphics.fill(leftPos + 5, topPos + 16, leftPos + imageWidth - 5, topPos + 47, 0xff252b30);
        int capacity = Math.max(1, menu.engineKind().builtIn() ? 1_600 : menu.engineKind().capacity());
        int fill = Math.min(196, (int) (196L * menu.engineResource() / capacity));
        graphics.fill(leftPos + 12, topPos + 37, leftPos + 208, topPos + 43, 0xff0c0f11);
        graphics.fill(leftPos + 12, topPos + 37, leftPos + 12 + fill, topPos + 43, 0xffd09a39);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xffe8f5f8, false);
        Component status = Component.translatable("screen.rail_beetle.remote.status",
                Component.translatable("engine.rail_beetle." + menu.engineKind().id()),
                com.bettercontent.railbeetle.upgrade.EngineKind.compactAmount(menu.engineResource()), menu.engineKind().unit());
        graphics.drawString(font, status, 10, 22, 0xffcbd2d6, false);
        if (!menu.engineKind().builtIn()) {
            graphics.drawString(font, Component.translatable("screen.rail_beetle.fallback", menu.fallbackFuel()),
                    10, 32, 0xff9faeb5, false);
        }
        graphics.drawString(font, Component.translatable("mode.rail_beetle." + menu.mode().name().toLowerCase(java.util.Locale.ROOT)),
                10, 84, 0xff9faeb5, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (var entry : controls.entrySet()) {
            if (!selected(entry.getKey())) continue;
            Button button = entry.getValue();
            graphics.fill(button.getX() + 3, button.getY() + button.getHeight() - 3,
                    button.getX() + button.getWidth() - 3, button.getY() + button.getHeight() - 1, 0xffffb33b);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private boolean selected(BeetleControl action) {
        return switch (action) {
            case REVERSE -> menu.mode().name().equals("MANUAL_REVERSE");
            case STOP -> !menu.mode().moves();
            case HALF_SPEED -> menu.mode().moves() && menu.speedTier().name().equals("HALF");
            case NORMAL_SPEED -> menu.mode().moves() && menu.speedTier().name().equals("NORMAL");
            case DOUBLE_SPEED -> menu.mode().moves() && menu.speedTier().name().equals("DOUBLE");
            case TRIPLE_SPEED -> menu.mode().moves() && menu.speedTier().name().equals("TRIPLE");
            case TOGGLE_SEARCHLIGHT -> menu.searchlightOn();
            default -> false;
        };
    }
}
