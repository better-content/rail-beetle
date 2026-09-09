package com.bettercontent.railbeetle.client;

import com.bettercontent.railbeetle.RailBeetleMod;
import com.bettercontent.railbeetle.RailBeetleRegistries;
import com.bettercontent.railbeetle.navigation.RouteProposal;
import com.bettercontent.railbeetle.navigation.RouteKind;
import com.bettercontent.railbeetle.navigation.RouteSupplyStatus;
import com.bettercontent.railbeetle.network.RailBeetleNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public final class RailBeetleClient {
    private static final int[][] COLORS = {
            {101, 184, 176}, {143, 163, 91}, {214, 154, 69}, {168, 115, 149},
            {112, 141, 177}, {212, 192, 106}, {184, 98, 82}
    };
    private static final int[] RUST = {145, 102, 72};
    private static final int[] WOOD = {126, 98, 67};
    private static final int[] PALE_WOOD = {188, 151, 96};
    private static final int[] BRASS = {226, 178, 76};
    private static final int[] CHARCOAL = {35, 28, 23};
    private static final double OCCLUDED_RANGE_SQR = 32.0 * 32.0;
    private static final KeyMapping SELECT_ROUTE = new KeyMapping(
            "key.rail_beetle.select_route", GLFW.GLFW_KEY_G, "key.categories.rail_beetle");

    private RailBeetleClient() {}

    @Mod.EventBusSubscriber(modid = RailBeetleMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(SELECT_ROUTE);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(RailBeetleRegistries.RAIL_BEETLE_ENTITY.get(),
                    RailBeetleRenderer::new);
        }

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(RailBeetleRegistries.RAIL_BEETLE_MENU.get(), RailBeetleScreen::new);
                MenuScreens.register(RailBeetleRegistries.REMOTE_BEETLE_MENU.get(), RemoteBeetleScreen::new);
                if (ModList.get().isLoaded("sodiumdynamiclights")) DynamicLightsCompat.register();
            });
        }
    }

    @Mod.EventBusSubscriber(modid = RailBeetleMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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
                RailBeetleNetwork.contextualAction(target.entityId(),
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
            int actionColor = 0xffffffff;
            if (target.action() == ClientRouteStore.Action.FOLLOW && target.route() != null) {
                RouteProposal route = target.route();
                int colorIndex = Math.floorMod(route.id(), COLORS.length);
                int[] color = COLORS[colorIndex];
                accent = 0xff000000 | color[0] << 16 | color[1] << 8 | color[2];
                Component routeName = Component.translatable("hud.rail_beetle.route." + colorIndex);
                if (route.kind() == RouteKind.BEACON) {
                    routeName = Component.translatable("hud.rail_beetle.route.beacon", routeName);
                }
                detail = Component.translatable("hud.rail_beetle.route", routeName,
                        route.railCount(), route.supportCount());
                if (target.supply().hasMissing()) {
                    action = Component.translatable("hud.rail_beetle.follow_missing", missingSupplies(target.supply()));
                    actionColor = 0xffff5555;
                } else {
                    action = Component.translatable("hud.rail_beetle.follow");
                }
            } else if (target.action() == ClientRouteStore.Action.STOP) {
                detail = Component.translatable("hud.rail_beetle.active");
                action = Component.translatable("hud.rail_beetle.stop");
            } else {
                detail = Component.translatable("hud.rail_beetle.stopped");
                action = Component.translatable("hud.rail_beetle.clear");
            }

            int width = Math.max(minecraft.font.width(detail), minecraft.font.width(action)) + 8;
            int x = Math.min(event.getWindow().getGuiScaledWidth() / 2 + 10,
                    event.getWindow().getGuiScaledWidth() - width - 4);
            int y = Math.min(event.getWindow().getGuiScaledHeight() / 2 + 9,
                    event.getWindow().getGuiScaledHeight() - 22);
            var graphics = event.getGuiGraphics();
            graphics.fill(x - 4, y - 3, x + width, y + 20, 0xb012171a);
            graphics.drawString(minecraft.font, detail, x, y, accent, true);
            graphics.drawString(minecraft.font, action, x, y + 10, actionColor, true);
        }

        private static Component missingSupplies(RouteSupplyStatus status) {
            MutableComponent result = Component.empty();
            boolean needsSeparator = false;
            if (status.missingRails() > 0) {
                result.append(Component.translatable("hud.rail_beetle.missing.rails", status.missingRails()));
                needsSeparator = true;
            }
            if (status.missingSupports() > 0) {
                if (needsSeparator) result.append(Component.literal(" · "));
                result.append(Component.translatable("hud.rail_beetle.missing.supports", status.missingSupports()));
                needsSeparator = true;
            }
            if (status.missingFuel()) {
                if (needsSeparator) result.append(Component.literal(" · "));
                result.append(Component.translatable("hud.rail_beetle.missing.fuel"));
            }
            return result;
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
            Matrix4f matrix = pose.last().pose();
            Matrix3f normal = pose.last().normal();

            if (selected != null) {
                ClientRouteStore.RouteSet set = ClientRouteStore.routes().get(selected.entityId());
                if (set != null) {
                    VertexConsumer occluded = minecraft.renderBuffers().bufferSource()
                            .getBuffer(RouteRenderTypes.OCCLUDED_LINES);
                    RouteOverlayGeometry.Track geometry = RouteOverlayGeometry.build(selected.route());
                    int[] accent = COLORS[Math.floorMod(selected.route().id(), COLORS.length)];
                    for (RouteOverlayGeometry.Segment segment : geometry.rails()) {
                        if (segment.midpoint().distanceToSqr(camera) <= OCCLUDED_RANGE_SQR) {
                            line(occluded, matrix, normal, segment.from(), segment.to(), accent, 0.30f);
                        }
                    }
                    for (RouteOverlayGeometry.Segment segment : geometry.sleepers()) {
                        if (segment.midpoint().distanceToSqr(camera) <= OCCLUDED_RANGE_SQR) {
                            line(occluded, matrix, normal, segment.from(), segment.to(), accent, 0.20f);
                        }
                    }
                    for (RouteOverlayGeometry.Segment segment : geometry.supports()) {
                        if (segment.midpoint().distanceToSqr(camera) <= OCCLUDED_RANGE_SQR) {
                            line(occluded, matrix, normal, segment.from(), segment.to(), PALE_WOOD, 0.22f);
                        }
                    }
                    for (RouteOverlayGeometry.Segment segment : geometry.pennant()) {
                        if (segment.midpoint().distanceToSqr(camera) <= OCCLUDED_RANGE_SQR) {
                            line(occluded, matrix, normal, segment.from(), segment.to(), accent, 0.34f);
                        }
                    }
                    minecraft.renderBuffers().bufferSource().endBatch(RouteRenderTypes.OCCLUDED_LINES);
                }
            }

            VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            float pulse = 0.88f + 0.12f * (float) Math.sin(
                    (minecraft.level.getGameTime() + event.getPartialTick()) * Math.PI / 50.0);
            for (Map.Entry<Integer, ClientRouteStore.RouteSet> entry : ClientRouteStore.routes().entrySet()) {
                var entity = minecraft.level.getEntity(entry.getKey());
                if (entity == null) continue;
                for (RouteProposal route : entry.getValue().proposals()) {
                    int[] accent = COLORS[Math.floorMod(route.id(), COLORS.length)];
                    boolean highlighted = selected != null && selected.entityId() == entry.getKey()
                            && selected.route().id() == route.id();
                    RouteOverlayGeometry.Track geometry = RouteOverlayGeometry.build(route);
                    renderTrack(consumer, matrix, normal, geometry, accent, highlighted, pulse);
                }
                RouteProposal active = entry.getValue().activeRoute();
                if (active != null && entity instanceof com.bettercontent.railbeetle.entity.RailBeetleEntity beetle) {
                    int first = Math.min(beetle.activeStep(), active.steps().size());
                    RouteOverlayGeometry.Track geometry = RouteOverlayGeometry.build(
                            active, first, entity.position());
                    renderActiveTrack(consumer, matrix, normal, geometry,
                            COLORS[Math.floorMod(active.id(), COLORS.length)]);
                }
            }
            pose.popPose();
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }

        private static void renderTrack(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                                        RouteOverlayGeometry.Track track, int[] accent,
                                        boolean highlighted, float pulse) {
            if (highlighted) {
                for (RouteOverlayGeometry.Segment segment : track.rails()) {
                    outlinedLine(consumer, matrix, normal, segment, BRASS, pulse);
                }
                for (RouteOverlayGeometry.Segment segment : track.sleepers()) {
                    outlinedLine(consumer, matrix, normal, segment, PALE_WOOD, pulse * 0.88f);
                }
                for (RouteOverlayGeometry.Segment segment : track.ties()) {
                    outlinedLine(consumer, matrix, normal, segment, accent, pulse);
                }
                for (RouteOverlayGeometry.Segment segment : track.supports()) {
                    outlinedLine(consumer, matrix, normal, segment, PALE_WOOD, pulse * 0.78f);
                }
                for (RouteOverlayGeometry.Segment segment : track.pennant()) {
                    outlinedLine(consumer, matrix, normal, segment, accent, pulse);
                }
                return;
            }
            for (RouteOverlayGeometry.Segment segment : track.rails()) {
                line(consumer, matrix, normal, segment.from(), segment.to(), RUST, 0.42f);
            }
            for (RouteOverlayGeometry.Segment segment : track.sleepers()) {
                line(consumer, matrix, normal, segment.from(), segment.to(), WOOD, 0.34f);
            }
            for (RouteOverlayGeometry.Segment segment : track.ties()) {
                line(consumer, matrix, normal, segment.from(), segment.to(), accent, 0.72f);
            }
            for (RouteOverlayGeometry.Segment segment : track.supports()) {
                line(consumer, matrix, normal, segment.from(), segment.to(), WOOD, 0.38f);
            }
            for (RouteOverlayGeometry.Segment segment : track.pennant()) {
                line(consumer, matrix, normal, segment.from(), segment.to(), accent, 0.78f);
            }
        }

        private static void renderActiveTrack(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                                              RouteOverlayGeometry.Track track, int[] accent) {
            for (RouteOverlayGeometry.Segment segment : track.rails()) {
                outlinedLine(consumer, matrix, normal, segment, BRASS, 0.94f);
            }
            for (RouteOverlayGeometry.Segment segment : track.sleepers()) {
                outlinedLine(consumer, matrix, normal, segment, PALE_WOOD, 0.82f);
            }
            for (RouteOverlayGeometry.Segment segment : track.ties()) {
                outlinedLine(consumer, matrix, normal, segment, accent, 0.92f);
            }
            for (RouteOverlayGeometry.Segment segment : track.supports()) {
                outlinedLine(consumer, matrix, normal, segment, PALE_WOOD, 0.78f);
            }
            for (RouteOverlayGeometry.Segment segment : track.pennant()) {
                outlinedLine(consumer, matrix, normal, segment, accent, 0.94f);
            }
        }

        private static void outlinedLine(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                                         RouteOverlayGeometry.Segment segment, int[] color, float alpha) {
            Vec3 offsetX = new Vec3(0.012, -0.008, 0);
            Vec3 offsetZ = new Vec3(0, -0.008, 0.012);
            line(consumer, matrix, normal, segment.from().add(offsetX), segment.to().add(offsetX),
                    CHARCOAL, alpha * 0.82f);
            line(consumer, matrix, normal, segment.from().subtract(offsetX), segment.to().subtract(offsetX),
                    CHARCOAL, alpha * 0.82f);
            line(consumer, matrix, normal, segment.from().add(offsetZ), segment.to().add(offsetZ),
                    CHARCOAL, alpha * 0.82f);
            line(consumer, matrix, normal, segment.from().subtract(offsetZ), segment.to().subtract(offsetZ),
                    CHARCOAL, alpha * 0.82f);
            line(consumer, matrix, normal, segment.from(), segment.to(), color, alpha);
        }

        private static void line(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                                 Vec3 from, Vec3 to, int[] color, float alpha) {
            float nx = (float) (to.x - from.x);
            float ny = (float) (to.y - from.y);
            float nz = (float) (to.z - from.z);
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1.0e-5f) return;
            nx /= length;
            ny /= length;
            nz /= length;
            consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                    .color(color[0], color[1], color[2], (int) (alpha * 255)).normal(normal, nx, ny, nz).endVertex();
            consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                    .color(color[0], color[1], color[2], (int) (alpha * 255)).normal(normal, nx, ny, nz).endVertex();
        }
    }
}
