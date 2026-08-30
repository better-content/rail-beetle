package com.bettercontent.railscout.network;

import com.bettercontent.railscout.RailScoutMod;
import com.bettercontent.railscout.client.ClientRouteStore;
import com.bettercontent.railscout.entity.RailScoutEntity;
import com.bettercontent.railscout.navigation.RouteProposal;
import com.bettercontent.railscout.navigation.RouteStep;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class RailScoutNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RailScoutMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private RailScoutNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ProposalSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProposalSync::encode).decoder(ProposalSync::decode)
                .consumerMainThread(ProposalSync::handle).add();
        CHANNEL.messageBuilder(SelectRoute.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectRoute::encode).decoder(SelectRoute::decode)
                .consumerMainThread(SelectRoute::handle).add();
        CHANNEL.messageBuilder(Control.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Control::encode).decoder(Control::decode)
                .consumerMainThread(Control::handle).add();
    }

    public static void syncProposals(RailScoutEntity scout, List<RouteProposal> proposals) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> scout), new ProposalSync(scout.getId(), proposals));
    }

    public static void selectRoute(int entityId, long generation, int routeId) {
        CHANNEL.sendToServer(new SelectRoute(entityId, generation, routeId));
    }

    public static void control(int entityId, ScoutControl control) {
        CHANNEL.sendToServer(new Control(entityId, control));
    }

    public record ProposalSync(int entityId, List<RouteProposal> proposals) {
        private static void encode(ProposalSync packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeCollection(packet.proposals, (out, proposal) -> {
                out.writeVarInt(proposal.id());
                out.writeLong(proposal.generation());
                out.writeBlockPos(proposal.endpoint());
                out.writeCollection(proposal.steps(), (stepOut, step) -> {
                    stepOut.writeBlockPos(step.railPos());
                    stepOut.writeEnum(step.shape());
                    stepOut.writeBoolean(step.supportPos() != null);
                    if (step.supportPos() != null) stepOut.writeBlockPos(step.supportPos());
                });
            });
        }

        private static ProposalSync decode(FriendlyByteBuf buffer) {
            int entityId = buffer.readVarInt();
            List<RouteProposal> proposals = buffer.readList(in -> {
                int id = in.readVarInt();
                long generation = in.readLong();
                BlockPos endpoint = in.readBlockPos();
                List<RouteStep> steps = in.readList(stepIn -> {
                    BlockPos railPos = stepIn.readBlockPos();
                    RailShape shape = stepIn.readEnum(RailShape.class);
                    BlockPos support = stepIn.readBoolean() ? stepIn.readBlockPos() : null;
                    return new RouteStep(railPos, shape, support);
                });
                return new RouteProposal(id, generation, endpoint, steps);
            });
            return new ProposalSync(entityId, proposals);
        }

        private static void handle(ProposalSync packet, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRouteStore.receive(packet.entityId, packet.proposals));
            context.get().setPacketHandled(true);
        }
    }

    public record SelectRoute(int entityId, long generation, int routeId) {
        private static void encode(SelectRoute packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeLong(packet.generation);
            buffer.writeVarInt(packet.routeId);
        }

        private static SelectRoute decode(FriendlyByteBuf buffer) {
            return new SelectRoute(buffer.readVarInt(), buffer.readLong(), buffer.readVarInt());
        }

        private static void handle(SelectRoute packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && player.level().getEntity(packet.entityId) instanceof RailScoutEntity scout) {
                scout.selectRoute(player, packet.generation, packet.routeId);
            }
            context.get().setPacketHandled(true);
        }
    }

    public record Control(int entityId, ScoutControl action) {
        private static void encode(Control packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeEnum(packet.action);
        }

        private static Control decode(FriendlyByteBuf buffer) {
            return new Control(buffer.readVarInt(), buffer.readEnum(ScoutControl.class));
        }

        private static void handle(Control packet, Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && player.level().getEntity(packet.entityId) instanceof RailScoutEntity scout) {
                scout.control(player, packet.action);
            }
            context.get().setPacketHandled(true);
        }
    }
}
