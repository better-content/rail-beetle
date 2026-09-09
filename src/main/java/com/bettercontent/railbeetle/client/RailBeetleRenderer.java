package com.bettercontent.railbeetle.client;

import com.bettercontent.railbeetle.entity.BeetleMode;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import com.bettercontent.railbeetle.upgrade.BeetleProfile;
import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** An industrial rail automaton whose functional parts happen to form a beetle-like silhouette. */
public final class RailBeetleRenderer extends EntityRenderer<RailBeetleEntity> {
    private static final float[] STEEL = rgb(32, 38, 42), STEEL_EDGE = rgb(62, 70, 73);
    private static final float[] BRASS = rgb(171, 127, 53), AGED_BRASS = rgb(116, 90, 46);
    private static final float[] COPPER = rgb(151, 78, 48), TIMBER = rgb(92, 62, 37);
    private static final float[] AMBER = rgb(255, 170, 42), RED = rgb(148, 45, 34);
    private static final float[] BLUE = rgb(54, 111, 164), VIOLET = rgb(127, 67, 166);
    private static final float[] CRIMSON = rgb(130, 23, 41), CYAN = rgb(57, 163, 177);
    private static final float[] PALE = rgb(200, 197, 155);

    public RailBeetleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.85f;
    }

    @Override
    public void render(RailBeetleEntity beetle, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.translate(0, 0.12, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - beetle.noseHeading().toYRot()));
        float phase = (beetle.tickCount + partialTick) * (beetle.mode().moves() ? 0.45f : 0.08f);
        float working = beetle.mode() == BeetleMode.AUTO_BUILD || beetle.mode() == BeetleMode.PLANNING ? 1.0f : 0.0f;
        VertexConsumer solid = buffers.getBuffer(RenderType.debugFilledBox());

        box(pose, solid, -0.70, 0.02, -0.98, 0.70, 0.19, 0.83, STEEL);
        box(pose, solid, -0.62, 0.19, -0.30, 0.62, 0.52, 0.78, TIMBER);
        box(pose, solid, -0.67, 0.48, -0.35, 0.67, 0.58, 0.83, AGED_BRASS);
        box(pose, solid, -0.50, 0.58, -0.18, 0.50, 0.86, 0.62, STEEL);
        box(pose, solid, -0.17, 0.86, -0.02, 0.17, 1.05, 0.39, BRASS);
        box(pose, solid, -0.58, 0.55, -0.22, -0.52, 0.75, 0.60, COPPER);
        box(pose, solid, 0.52, 0.55, -0.22, 0.58, 0.75, 0.60, COPPER);
        box(pose, solid, -0.28, 0.70, -0.24, 0.28, 0.86, -0.15, AGED_BRASS);
        box(pose, solid, -0.20, 0.74, -0.255, 0.20, 0.82, -0.245, AMBER);

        renderCovers(beetle, pose, solid, phase, working);
        renderMandibles(pose, solid, phase, working);
        renderSurveyBooms(beetle, pose, solid, phase);
        renderToolAssemblies(beetle, pose, solid, phase);
        renderEngine(beetle.engineKind(), pose, solid, phase);
        renderModules(beetle.profile(), pose, solid, phase);
        renderSearchlight(beetle, pose, solid);
        pose.popPose();
        super.render(beetle, yaw, partialTick, pose, buffers, packedLight);
    }

    private static void renderCovers(RailBeetleEntity beetle, PoseStack pose, VertexConsumer solid,
                                     float phase, float working) {
        float lift = working * (11.0f + 3.0f * (float) Math.sin(phase));
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            pose.translate(side * 0.12, 0.80, 0.22);
            pose.mulPose(Axis.ZP.rotationDegrees(side * -lift));
            box(pose, solid, side < 0 ? -0.53 : 0.02, 0, -0.34,
                    side < 0 ? -0.02 : 0.53, 0.10, 0.40, STEEL_EDGE);
            box(pose, solid, side < 0 ? -0.45 : 0.08, 0.08, -0.26,
                    side < 0 ? -0.08 : 0.45, 0.13, 0.32, BRASS);
            pose.popPose();
        }
    }

    private static void renderMandibles(PoseStack pose, VertexConsumer solid, float phase, float working) {
        float bite = working * (6.0f + 7.0f * (float) Math.sin(phase * 1.7f));
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            pose.translate(side * 0.30, 0.30, -0.86);
            pose.mulPose(Axis.YP.rotationDegrees(side * bite));
            box(pose, solid, side < 0 ? -0.28 : -0.03, -0.06, -0.45,
                    side < 0 ? 0.03 : 0.28, 0.07, 0.05, BRASS);
            box(pose, solid, side < 0 ? -0.32 : 0.18, -0.13, -0.52,
                    side < 0 ? -0.18 : 0.32, 0.03, -0.35, STEEL_EDGE);
            pose.popPose();
        }
    }

    private static void renderSurveyBooms(RailBeetleEntity beetle, PoseStack pose, VertexConsumer solid, float phase) {
        float extension = beetle.mode() == BeetleMode.PLANNING || beetle.mode() == BeetleMode.READY ? 0.36f : 0.0f;
        extension += beetle.profile().surveyTier() * 0.10f;
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            pose.translate(side * 0.27, 0.88, -0.46);
            pose.mulPose(Axis.XP.rotationDegrees(side * 2.0f * (float) Math.sin(phase * 0.6f)));
            box(pose, solid, -0.035, 0, -0.40 - extension, 0.035, 0.07, 0.04, COPPER);
            box(pose, solid, -0.075, -0.02, -0.46 - extension, 0.075, 0.11, -0.35 - extension, AMBER);
            pose.popPose();
        }
    }

    private static void renderToolAssemblies(RailBeetleEntity beetle, PoseStack pose, VertexConsumer solid, float phase) {
        for (int side : new int[]{-1, 1}) for (int index = 0; index < 3; index++) {
            double z = -0.48 + index * 0.52;
            float pump = beetle.mode().moves() ? (float) Math.sin(phase + index * 2.1f) * 0.06f : 0;
            pose.pushPose();
            pose.translate(side * 0.62, 0.31 + pump, z);
            pose.mulPose(Axis.ZP.rotationDegrees(side * (18.0f + pump * 90.0f)));
            box(pose, solid, side < 0 ? -0.27 : -0.02, -0.045, -0.06,
                    side < 0 ? 0.02 : 0.27, 0.045, 0.06, STEEL_EDGE);
            box(pose, solid, side < 0 ? -0.33 : 0.22, -0.10, -0.10,
                    side < 0 ? -0.22 : 0.33, 0.10, 0.10,
                    index == 1 && beetle.profile().brakeTier() > 0 ? RED : AGED_BRASS);
            pose.popPose();
        }
    }

    private static void renderEngine(EngineKind kind, PoseStack pose, VertexConsumer solid, float phase) {
        float[] accent = switch (kind) {
            case STEAM -> COPPER; case FLUX -> RED; case SOURCE -> VIOLET; case LIFEFORCE -> CRIMSON;
            case PRESSURE -> CYAN; case SOUL -> PALE; case SPIRIT -> rgb(116, 70, 134); default -> AMBER;
        };
        box(pose, solid, -0.34, 0.22, 0.48, 0.34, 0.67, 0.83, accent);
        box(pose, solid, -0.25, 0.64, 0.54, 0.25, 0.76, 0.78, STEEL);
        if (kind == EngineKind.STEAM) {
            double pulse = 0.04 * Math.sin(phase);
            box(pose, solid, -0.11, 0.75, 0.61, 0.11, 1.02 + pulse, 0.78, STEEL_EDGE);
        } else if (kind == EngineKind.FLUX || kind == EngineKind.PRESSURE) {
            box(pose, solid, -0.43, 0.34, 0.55, -0.34, 0.57, 0.77, BRASS);
            box(pose, solid, 0.34, 0.34, 0.55, 0.43, 0.57, 0.77, BRASS);
        }
    }

    private static void renderModules(BeetleProfile p, PoseStack pose, VertexConsumer solid, float phase) {
        if (p.governorTier() > 0) {
            pose.pushPose();
            pose.translate(0, 0.98, 0.28);
            pose.mulPose(Axis.YP.rotationDegrees(phase * (p.governorTier() == 2 ? 32.0f : 18.0f)));
            box(pose, solid, -0.23, -0.03, -0.035, 0.23, 0.03, 0.035, BRASS);
            box(pose, solid, -0.035, -0.03, -0.23, 0.035, 0.03, 0.23, BRASS);
            pose.popPose();
        }
        if (p.recuperatorTier() > 0) box(pose, solid, -0.45, 0.68, 0.60, -0.34, 0.93, 0.75, COPPER);
        if (p.adhesionTier() > 0) {
            box(pose, solid, -0.70, 0.12, -0.70, -0.61, 0.31, -0.35, AGED_BRASS);
            box(pose, solid, 0.61, 0.12, -0.70, 0.70, 0.31, -0.35, AGED_BRASS);
        }
        if (p.torqueTier() > 0) box(pose, solid, -0.40, 0.15, 0.24, 0.40, 0.27, 0.38, BRASS);
        if (p.drawgearTier() > 0) box(pose, solid, -0.17, 0.13, 0.80, 0.17, 0.29, 1.08, STEEL_EDGE);
        if (p.remoteTier() > 0) {
            box(pose, solid, -0.04, 1.00, 0.41, 0.04, 1.20 + p.remoteTier() * 0.08, 0.49, COPPER);
            box(pose, solid, -0.12, 1.18 + p.remoteTier() * 0.08, 0.38, 0.12, 1.24 + p.remoteTier() * 0.08, 0.52, AMBER);
        }
        if (p.trestleTier() > 0) {
            box(pose, solid, -0.66, 0.40, -0.16, -0.57, 0.67, 0.45, BRASS);
            box(pose, solid, 0.57, 0.40, -0.16, 0.66, 0.67, 0.45, BRASS);
        }
    }

    private static void renderSearchlight(RailBeetleEntity beetle, PoseStack pose, VertexConsumer solid) {
        if (!beetle.profile().searchlight()) return;
        box(pose, solid, -0.17, 0.82, -0.48, 0.17, 1.06, -0.27, AGED_BRASS);
        box(pose, solid, -0.12, 0.86, -0.495, 0.12, 1.02, -0.485,
                beetle.searchlightOn() ? AMBER : STEEL_EDGE);
    }

    private static void box(PoseStack pose, VertexConsumer consumer, double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ, float[] color) {
        LevelRenderer.addChainedFilledBoxVertices(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                color[0], color[1], color[2], 1.0f);
    }

    private static float[] rgb(int red, int green, int blue) {
        return new float[]{red / 255.0f, green / 255.0f, blue / 255.0f};
    }

    @Override public ResourceLocation getTextureLocation(RailBeetleEntity entity) { return InventoryMenu.BLOCK_ATLAS; }
}
