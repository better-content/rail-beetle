package com.bettercontent.railbeetle.harness;

import com.bettercontent.railbeetle.RailBeetleRegistries;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import com.bettercontent.railbeetle.item.EngineItem;
import com.bettercontent.railbeetle.item.RemoteControlItem;
import com.bettercontent.railbeetle.network.BeetleControl;
import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.bettercontent.railbeetle.upgrade.ModuleKind;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(VisualHarnessMod.MOD_ID)
public final class VisualHarnessMod {
    public static final String MOD_ID = "rail_beetle_visual_harness";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(MOD_ID, "capture"))
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions("1"::equals)
            .serverAcceptedVersions("1"::equals)
            .simpleChannel();

    public VisualHarnessMod() {
        CHANNEL.messageBuilder(CapturePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CapturePacket::encode)
                .decoder(CapturePacket::decode)
                .consumerMainThread(CapturePacket::handle)
                .add();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rbvisual").requires(source -> source.hasPermission(2))
                .then(Commands.literal("main")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> open(
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "variant"),
                                                        StringArgumentType.getString(context, "name"), false))))))
                .then(Commands.literal("remote")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> open(
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "variant"),
                                                        StringArgumentType.getString(context, "name"), true)))))));
    }

    private static int open(ServerPlayer player, String variant, String name, boolean remote) {
        if (!variant.equals("baseline") && !variant.equals("upgraded") && !variant.equals("locked")) {
            player.sendSystemMessage(Component.literal("variant must be baseline, upgraded, or locked"));
            return 0;
        }
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(player.serverLevel());
        if (beetle == null) return 0;
        beetle.setPos(player.getX(), player.getY(), player.getZ());
        player.serverLevel().addFreshEntity(beetle);
        beetle.inventory().setStackInSlot(0, new ItemStack(Items.RAIL, 64));
        beetle.inventory().setStackInSlot(1, new ItemStack(Items.STONE, 32));
        beetle.inventory().setStackInSlot(2, new ItemStack(Items.COAL, 16));
        if (!variant.equals("baseline")) installUpgrades(beetle);
        else if (remote) installRemoteReceiver(beetle);
        beetle.control(player, BeetleControl.STOP);
        if (variant.equals("locked")) beetle.control(player, BeetleControl.TOGGLE_NEUTRAL);
        if (remote) {
            ItemStack remoteControl = new ItemStack(RailBeetleRegistries.REMOTE_CONTROL.get());
            RemoteControlItem.bind(remoteControl, beetle);
            player.setItemInHand(InteractionHand.MAIN_HAND, remoteControl);
            NetworkHooks.openScreen(player, beetle.remoteMenuProvider(), beetle::writeRemoteOpenData);
        } else {
            NetworkHooks.openScreen(player, beetle, buffer -> buffer.writeVarInt(beetle.getId()));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CapturePacket(name));
        player.getServer().sendSystemMessage(Component.literal(
                "RAIL_BEETLE_VISUAL opened " + (remote ? "remote " : "main ") + variant + " as " + name));
        return 1;
    }

    private static void installUpgrades(RailBeetleEntity beetle) {
        ItemStack engine = new ItemStack(RailBeetleRegistries.ENGINE_ITEMS.get(EngineKind.STEAM).get());
        ((EngineItem) engine.getItem()).setResource(engine, EngineKind.STEAM.capacity() / 2);
        beetle.engineInventory().setStackInSlot(0, engine);
        beetle.moduleInventory().setStackInSlot(0,
                new ItemStack(RailBeetleRegistries.MODULE_ITEMS.get(ModuleKind.HIGH_SPEED_GOVERNOR_II).get()));
        beetle.moduleInventory().setStackInSlot(1,
                new ItemStack(RailBeetleRegistries.MODULE_ITEMS.get(ModuleKind.DISPATCH_RECEIVER_II).get()));
        beetle.moduleInventory().setStackInSlot(2,
                new ItemStack(RailBeetleRegistries.MODULE_ITEMS.get(ModuleKind.CAGED_SEARCHLIGHT).get()));
        beetle.toggleSearchlight();
    }

    private static void installRemoteReceiver(RailBeetleEntity beetle) {
        beetle.moduleInventory().setStackInSlot(0,
                new ItemStack(RailBeetleRegistries.MODULE_ITEMS.get(ModuleKind.DISPATCH_RECEIVER_I).get()));
    }

    record CapturePacket(String name) {
        static void encode(CapturePacket packet, FriendlyByteBuf buffer) { buffer.writeUtf(packet.name, 64); }
        static CapturePacket decode(FriendlyByteBuf buffer) { return new CapturePacket(buffer.readUtf(64)); }
        static void handle(CapturePacket packet,
                           java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            supplier.get().setPacketHandled(true);
            VisualHarnessScreenshot.request(packet.name);
        }
    }
}
