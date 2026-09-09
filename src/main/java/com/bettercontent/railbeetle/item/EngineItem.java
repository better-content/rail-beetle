package com.bettercontent.railbeetle.item;

import com.bettercontent.railbeetle.upgrade.EngineKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public final class EngineItem extends Item {
    public static final String RESOURCE_TAG = "RailBeetleResource";
    public static final String STEAM_WATER_TAG = "RailBeetleWater";
    private final EngineKind kind;

    public EngineItem(EngineKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public EngineKind kind() { return kind; }
    public int resource(ItemStack stack) { return Math.max(0, stack.getOrCreateTag().getInt(RESOURCE_TAG)); }
    public void setResource(ItemStack stack, int amount) {
        stack.getOrCreateTag().putInt(RESOURCE_TAG, Math.max(0, Math.min(kind.capacity(), amount)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.rail_beetle.engine.source", kind.requiredMod()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.rail_beetle.engine.stored", resource(stack), kind.capacity(), kind.unit()).withStyle(ChatFormatting.GOLD));
        if (kind == EngineKind.STEAM) {
            tooltip.add(Component.translatable("tooltip.rail_beetle.engine.water",
                    stack.getOrCreateTag().getInt(STEAM_WATER_TAG), 8_000).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.rail_beetle.engine." + kind.id()).withStyle(ChatFormatting.GRAY));
    }
}
