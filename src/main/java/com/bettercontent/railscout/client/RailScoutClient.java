package com.bettercontent.railscout.client;

import com.bettercontent.railscout.RailScoutMod;
import com.bettercontent.railscout.RailScoutRegistries;
import com.bettercontent.railscout.navigation.RouteProposal;
import com.bettercontent.railscout.network.RailScoutNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public final class RailScoutClient {
    private static final int[][] COLORS = {{40, 220, 255}, {90, 255, 120}, {255, 190, 55}};
    private static final KeyMapping SELECT_ROUTE = new KeyMapping(
            "key.rail_scout.select_route", GLFW.GLFW_KEY_G, "key.categories.rail_scout");

    private RailScoutClient() {}

    @Mod.EventBusSubscriber(modid = RailScoutMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(SELECT_ROUTE);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(RailScoutRegistries.RAIL_SCOUT_ENTITY.get(),
                    context -> new MinecartRenderer<>(context, ModelLayers.MINECART));
        }

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(RailScoutRegistries.RAIL_SCOUT_MENU.get(), RailScoutScreen::new));
        }
    }

    @Mod.EventBusSubscriber(modid = RailScoutMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeEvents {
        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();
            while (SELECT_ROUTE.consumeClick()) {
                if (minecraft.screen != null) continue;
                ClientRouteStore.Selection selected = ClientRouteStore.crosshairSelection();
                if (selected == null) continue;
                RouteProposal route = selected.route();
                RailScoutNetwork.selectRoute(selected.entityId(), route.generation(), route.id());
            }
            ClientRouteStore.Selection selected = ClientRouteStore.crosshairSelection();
            if (selected != null && minecraft.player != null) {
                RouteProposal route = selected.route();
                minecraft.player.displayClientMessage(Component.translatable(
                        "message.rail_scout.crosshair", route.railCount(), route.supportCount()), true);
            }
        }

        @SubscribeEvent
        public static void renderRoutes(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            Vec3 camera = event.getCamera().getPosition();
            ClientRouteStore.Selection selected = ClientRouteStore.crosshairSelection();
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            Matrix4f matrix = pose.last().pose();
            Matrix3f normal = pose.last().normal();
            for (Map.Entry<Integer, java.util.List<RouteProposal>> entry : ClientRouteStore.routes().entrySet()) {
                var entity = minecraft.level.getEntity(entry.getKey());
                if (entity == null) continue;
                for (RouteProposal route : entry.getValue()) {
                    int[] color = COLORS[Math.floorMod(route.id(), COLORS.length)];
                    boolean highlighted = selected != null && selected.entityId() == entry.getKey()
                            && selected.route().id() == route.id();
                    float alpha = highlighted ? 1.0f : 0.65f;
                    Vec3 previous = entity.position().add(0, 0.2, 0);
                    for (var step : route.steps()) {
                        Vec3 current = Vec3.atLowerCornerOf(step.railPos()).add(0.5, 0.2, 0.5);
                        line(consumer, matrix, normal, previous, current, color, alpha);
                        previous = current;
                    }
                }
            }
            pose.popPose();
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }

        private static void line(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                                 Vec3 from, Vec3 to, int[] color, float alpha) {
            float nx = (float) (to.x - from.x);
            float ny = (float) (to.y - from.y);
            float nz = (float) (to.z - from.z);
            consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                    .color(color[0], color[1], color[2], (int) (alpha * 255)).normal(normal, nx, ny, nz).endVertex();
            consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                    .color(color[0], color[1], color[2], (int) (alpha * 255)).normal(normal, nx, ny, nz).endVertex();
        }
    }
}
