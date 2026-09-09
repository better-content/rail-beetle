package com.bettercontent.railbeetle.item;

import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public final class RemoteControlItem extends Item {
    private static final String TARGET = "RailBeetleTarget";
    private static final String DIMENSION = "RailBeetleDimension";

    public RemoteControlItem(Properties properties) { super(properties); }

    public static void bind(ItemStack stack, RailBeetleEntity beetle) {
        stack.getOrCreateTag().putUUID(TARGET, beetle.getUUID());
        stack.getOrCreateTag().putString(DIMENSION, beetle.level().dimension().location().toString());
    }

    @Nullable
    public static UUID target(ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID(TARGET) ? stack.getTag().getUUID(TARGET) : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        UUID id = target(stack);
        if (id == null) {
            player.displayClientMessage(Component.translatable("message.rail_beetle.remote.unbound"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!(level instanceof ServerLevel server) || !(server.getEntity(id) instanceof RailBeetleEntity beetle)) {
            player.displayClientMessage(Component.translatable("message.rail_beetle.remote.offline"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!beetle.canRemoteControl(player)) {
            player.displayClientMessage(Component.translatable("message.rail_beetle.remote.out_of_range"), true);
            return InteractionResultHolder.fail(stack);
        }
        NetworkHooks.openScreen((ServerPlayer) player, beetle.remoteMenuProvider(), beetle::writeRemoteOpenData);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID id = target(stack);
        tooltip.add(Component.translatable(id == null ? "tooltip.rail_beetle.remote.unbound" : "tooltip.rail_beetle.remote.bound",
                id == null ? "" : id.toString().substring(0, 8)).withStyle(id == null ? ChatFormatting.GRAY : ChatFormatting.GOLD));
    }
}
