package com.bettercontent.railbeetle.network;

import com.bettercontent.railbeetle.RailBeetleMod;
import com.bettercontent.railbeetle.client.ClientRouteStore;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import com.bettercontent.railbeetle.navigation.RouteProposal;
import com.bettercontent.railbeetle.navigation.RouteKind;
import com.bettercontent.railbeetle.navigation.RouteSupplyStatus;
import com.bettercontent.railbeetle.navigation.RouteStep;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class RailBeetleNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RailBeetleMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private RailBeetleNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(RouteSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RouteSync::encode).decoder(RouteSync::decode)
                .consumerMainThread(RouteSync::handle).add();
        CHANNEL.messageBuilder(ContextualAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ContextualAction::encode).decoder(ContextualAction::decode)
                .consumerMainThread(ContextualAction::handle).add();
        CHANNEL.messageBuilder(Control.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Control::encode).decoder(Control::decode)
                .consumerMainThread(Control::handle).add();
    }

    public static void syncRoutes(RailBeetleEntity beetle, List<RouteProposal> proposals, @Nullable RouteProposal activeRoute) {
        List<RouteSupplyStatus> supplies = proposals.stream()
                .map(route -> beetle.supplyStatus(route)).toList();
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> beetle),
                new RouteSync(beetle.getId(), proposals, supplies, activeRoute));
    }

    public static void syncRoutesTo(ServerPlayer player, RailBeetleEntity beetle,
                                    List<RouteProposal> proposals, @Nullable RouteProposal activeRoute) {
        List<RouteSupplyStatus> supplies = proposals.stream()
                .map(route -> beetle.supplyStatus(route)).toList();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RouteSync(beetle.getId(), proposals, supplies, activeRoute));
    }

    public static void contextualAction(int entityId, long generation, int routeId) {
        CHANNEL.sendToServer(new ContextualAction(entityId, generation, routeId));
    }

    public static void control(int entityId, BeetleControl control) {
        CHANNEL.sendToServer(new Control(entityId, control));
    }

    public record RouteSync(int entityId, List<RouteProposal> proposals, List<RouteSupplyStatus> supplies,
                            @Nullable RouteProposal activeRoute) {
        private static void encode(RouteSync packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeCollection(packet.proposals, RailBeetleNetwork::writeRoute);
            buffer.writeCollection(packet.supplies, (out, status) -> {
                out.writeVarInt(status.missingRails());
                out.writeVarInt(status.missingSupports());
                out.writeBoolean(status.missingFuel());
            });
            buffer.writeBoolean(packet.activeRoute != null);
            if (packet.activeRoute != null) writeRoute(buffer, packet.activeRoute);
        }

        private static RouteSync decode(FriendlyByteBuf buffer) {
            int entityId = buffer.readVarInt();
            List<RouteProposal> proposals = buffer.readList(RailBeetleNetwork::readRoute);
            List<RouteSupplyStatus> supplies = buffer.readList(in -> new RouteSupplyStatus(
                    in.readVarInt(), in.readVarInt(), in.readBoolean()));
            RouteProposal active = buffer.readBoolean() ? readRoute(buffer) : null;
            return new RouteSync(entityId, proposals, supplies, active);
        }

        private static void handle(RouteSync packet, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientRouteStore.receive(packet.entityId, packet.proposals,
                            packet.supplies, packet.activeRoute));
            context.get().setPacketHandled(true);
        }
    }

    public record ContextualAction(int entityId, long generation, int routeId) {
        private static void encode(ContextualAction packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeLong(packet.generation);
            buffer.writeVarInt(packet.routeId);
        }

        private static ContextualAction decode(FriendlyByteBuf buffer) {
            return new ContextualAction(buffer.readVarInt(), buffer.readLong(), buffer.readVarInt());
        }

        private static void handle(ContextualAction packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && player.level().getEntity(packet.entityId) instanceof RailBeetleEntity beetle) {
                beetle.contextualAction(player, packet.generation, packet.routeId);
            }
            context.get().setPacketHandled(true);
        }
    }

    public record Control(int entityId, BeetleControl action) {
        private static void encode(Control packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeEnum(packet.action);
        }

        private static Control decode(FriendlyByteBuf buffer) {
            return new Control(buffer.readVarInt(), buffer.readEnum(BeetleControl.class));
        }

        private static void handle(Control packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && player.level().getEntity(packet.entityId) instanceof RailBeetleEntity beetle) {
                beetle.control(player, packet.action);
            }
            context.get().setPacketHandled(true);
        }
    }

    private static void writeRoute(FriendlyByteBuf out, RouteProposal route) {
        out.writeVarInt(route.id());
        out.writeLong(route.generation());
        out.writeBlockPos(route.origin());
        out.writeEnum(route.originHeading());
        out.writeBlockPos(route.endpoint());
        out.writeEnum(route.kind());
        out.writeBoolean(route.beaconTarget() != null);
        if (route.beaconTarget() != null) out.writeBlockPos(route.beaconTarget());
        out.writeCollection(route.steps(), (stepOut, step) -> {
            stepOut.writeBlockPos(step.railPos());
            stepOut.writeEnum(step.shape());
            stepOut.writeCollection(step.supportPositions(), FriendlyByteBuf::writeBlockPos);
        });
    }

    private static RouteProposal readRoute(FriendlyByteBuf in) {
        int id = in.readVarInt();
        long generation = in.readLong();
        BlockPos origin = in.readBlockPos();
        net.minecraft.core.Direction originHeading = in.readEnum(net.minecraft.core.Direction.class);
        BlockPos endpoint = in.readBlockPos();
        RouteKind kind = in.readEnum(RouteKind.class);
        BlockPos beaconTarget = in.readBoolean() ? in.readBlockPos() : null;
        List<RouteStep> steps = in.readCollection(ArrayList::new, stepIn -> {
            BlockPos railPos = stepIn.readBlockPos();
            RailShape shape = stepIn.readEnum(RailShape.class);
            List<BlockPos> supports = stepIn.readList(FriendlyByteBuf::readBlockPos);
            return new RouteStep(railPos, shape, supports);
        });
        return new RouteProposal(id, generation, origin, originHeading, endpoint, steps, kind, beaconTarget);
    }
}
