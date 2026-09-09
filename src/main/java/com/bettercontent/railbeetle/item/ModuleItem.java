package com.bettercontent.railbeetle.item;

import com.bettercontent.railbeetle.upgrade.ModuleKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public final class ModuleItem extends Item {
    private final ModuleKind kind;

    public ModuleItem(ModuleKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public ModuleKind kind() { return kind; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.rail_beetle.module.tier", kind.tier()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.rail_beetle.module." + kind.id()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.rail_beetle.install_stopped").withStyle(ChatFormatting.DARK_GRAY));
    }
}
