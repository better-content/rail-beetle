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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public final class RailScoutClient {
    private static final int[][] COLORS = {
            {40, 220, 255}, {90, 255, 120}, {255, 190, 55}, {245, 95, 220},
            {105, 145, 255}, {255, 245, 90}, {255, 95, 95}
    };
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
                    RailScoutRenderer::new);
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
                ClientRouteStore.ActionTarget target = ClientRouteStore.contextualTarget();
                if (target == null) continue;
                RouteProposal route = target.route();
                RailScoutNetwork.contextualAction(target.entityId(),
                        route == null ? -1L : route.generation(), route == null ? -1 : route.id());
                ClientRouteStore.rememberControlled(target.entityId());
            }
        }

        @SubscribeEvent
        public static void renderHud(RenderGuiOverlayEvent.Post event) {
            if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
            ClientRouteStore.ActionTarget target = ClientRouteStore.contextualTarget();
            if (target == null) return;

            Component detail;
            Component action;
            int accent = 0xffd7e7ec;
            if (target.action() == ClientRouteStore.Action.FOLLOW && target.route() != null) {
                RouteProposal route = target.route();
                int colorIndex = Math.floorMod(route.id(), COLORS.length);
                int[] color = COLORS[colorIndex];
                accent = 0xff000000 | color[0] << 16 | color[1] << 8 | color[2];
                detail = Component.translatable("hud.rail_scout.route",
                        Component.translatable("hud.rail_scout.route." + colorIndex),
                        route.railCount(), route.supportCount());
                action = Component.translatable("hud.rail_scout.follow");
            } else if (target.action() == ClientRouteStore.Action.STOP) {
                detail = Component.translatable("hud.rail_scout.active");
                action = Component.translatable("hud.rail_scout.stop");
            } else {
                detail = Component.translatable("hud.rail_scout.stopped");
                action = Component.translatable("hud.rail_scout.clear");
            }

            int width = Math.max(minecraft.font.width(detail), minecraft.font.width(action)) + 8;
            int x = Math.min(event.getWindow().getGuiScaledWidth() / 2 + 10,
                    event.getWindow().getGuiScaledWidth() - width - 4);
            int y = Math.min(event.getWindow().getGuiScaledHeight() / 2 + 9,
                    event.getWindow().getGuiScaledHeight() - 22);
            var graphics = event.getGuiGraphics();
            graphics.fill(x - 4, y - 3, x + width, y + 20, 0xb012171a);
            graphics.drawString(minecraft.font, detail, x, y, accent, true);
            graphics.drawString(minecraft.font, action, x, y + 10, 0xffffffff, true);
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
            for (Map.Entry<Integer, ClientRouteStore.RouteSet> entry : ClientRouteStore.routes().entrySet()) {
                var entity = minecraft.level.getEntity(entry.getKey());
                if (entity == null) continue;
                for (RouteProposal route : entry.getValue().proposals()) {
                    int[] color = COLORS[Math.floorMod(route.id(), COLORS.length)];
                    boolean highlighted = selected != null && selected.entityId() == entry.getKey()
                            && selected.route().id() == route.id();
                    float alpha = highlighted ? 1.0f : 0.48f;
                    Vec3 previous = Vec3.atLowerCornerOf(route.origin()).add(0.5, 0.2, 0.5);
                    for (var step : route.steps()) {
                        Vec3 current = Vec3.atLowerCornerOf(step.railPos()).add(0.5, 0.2, 0.5);
                        line(consumer, matrix, normal, previous, current, color, alpha);
                        if (highlighted) {
                            line(consumer, matrix, normal, previous.add(0.018, 0.012, 0),
                                    current.add(0.018, 0.012, 0), color, alpha);
                            line(consumer, matrix, normal, previous.add(-0.018, -0.012, 0),
                                    current.add(-0.018, -0.012, 0), color, alpha);
                        }
                        previous = current;
                    }
                }
                RouteProposal active = entry.getValue().activeRoute();
                if (active != null && entity instanceof com.bettercontent.railscout.entity.RailScoutEntity scout) {
                    int[] color = COLORS[Math.floorMod(active.id(), COLORS.length)];
                    Vec3 previous = entity.position().add(0, 0.22, 0);
                    int first = Math.min(scout.activeStep(), active.steps().size());
                    for (int i = first; i < active.steps().size(); i++) {
                        Vec3 current = Vec3.atLowerCornerOf(active.steps().get(i).railPos()).add(0.5, 0.22, 0.5);
                        line(consumer, matrix, normal, previous, current, color, 0.92f);
                        line(consumer, matrix, normal, previous.add(0.018, 0.012, 0),
                                current.add(0.018, 0.012, 0), color, 0.92f);
                        line(consumer, matrix, normal, previous.add(-0.018, -0.012, 0),
                                current.add(-0.018, -0.012, 0), color, 0.92f);
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
