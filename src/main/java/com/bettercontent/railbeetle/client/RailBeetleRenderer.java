package com.bettercontent.railbeetle.client;

import com.bettercontent.railbeetle.entity.BeetleMode;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import com.bettercontent.railbeetle.upgrade.BeetleProfile;
import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** A compact track machine with a few beetle-like echoes in its paired armor and equipment. */
public final class RailBeetleRenderer extends EntityRenderer<RailBeetleEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("rail_beetle", "textures/entity/rail_beetle.png");

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
        MaterialBuffer materials = new MaterialBuffer(
                buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight);

        // Long underframe, running boards, buffer beams, hood, and cab establish the railway
        // silhouette before any upgrade-specific equipment is added.
        box(pose, materials, -0.62, 0.02, -1.20, 0.62, 0.18, 1.08, Material.TREAD);
        box(pose, materials, -0.70, 0.15, -1.12, -0.54, 0.28, 1.02, Material.STEEL_EDGE);
        box(pose, materials, 0.54, 0.15, -1.12, 0.70, 0.28, 1.02, Material.STEEL_EDGE);
        box(pose, materials, -0.74, 0.18, -1.24, 0.74, 0.32, -1.12, Material.AGED_BRASS);
        box(pose, materials, -0.69, 0.18, 1.01, 0.69, 0.31, 1.13, Material.AGED_BRASS);

        box(pose, materials, -0.50, 0.28, -0.94, 0.50, 0.69, 0.18, Material.STEEL);
        box(pose, materials, -0.44, 0.38, -0.955, 0.44, 0.61, -0.945, Material.GRILLE);
        box(pose, materials, -0.47, 0.32, 0.20, 0.47, 1.07, 0.91, Material.STEEL_EDGE);
        box(pose, materials, -0.39, 0.67, 0.19, 0.39, 0.96, 0.205, Material.AMBER);
        box(pose, materials, -0.485, 0.62, 0.35, -0.475, 0.94, 0.80, Material.CYAN);
        box(pose, materials, 0.475, 0.62, 0.35, 0.485, 0.94, 0.80, Material.CYAN);
        box(pose, materials, -0.55, 1.04, 0.13, 0.55, 1.17, 0.96, Material.AGED_BRASS);
        box(pose, materials, -0.42, 0.30, 0.91, 0.42, 0.70, 1.04, Material.TIMBER);

        renderCovers(beetle, pose, materials, phase, working);
        renderBuffers(pose, materials);
        renderSurveyBooms(beetle, pose, materials, phase);
        renderRunningGear(beetle, pose, materials, phase);
        renderEngine(beetle.engineKind(), pose, materials, phase);
        renderModules(beetle.profile(), pose, materials, phase);
        renderSearchlight(beetle, pose, materials);
        pose.popPose();
        super.render(beetle, yaw, partialTick, pose, buffers, packedLight);
    }

    private static void renderCovers(RailBeetleEntity beetle, PoseStack pose, MaterialBuffer materials,
                                     float phase, float working) {
        float lift = working * (9.0f + 2.0f * (float) Math.sin(phase));
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            pose.translate(side * 0.04, 0.69, -0.38);
            pose.mulPose(Axis.ZP.rotationDegrees(side * -lift));
            box(pose, materials, side < 0 ? -0.48 : 0.00, 0, -0.55,
                    side < 0 ? 0.00 : 0.48, 0.08, 0.55, Material.STEEL_EDGE);
            box(pose, materials, side < 0 ? -0.42 : 0.05, 0.07, -0.48,
                    side < 0 ? -0.05 : 0.42, 0.12, 0.48, Material.BRASS);
            pose.popPose();
        }
    }

    private static void renderBuffers(PoseStack pose, MaterialBuffer materials) {
        box(pose, materials, -0.55, 0.20, -1.38, -0.35, 0.38, -1.22, Material.STEEL_EDGE);
        box(pose, materials, 0.35, 0.20, -1.38, 0.55, 0.38, -1.22, Material.STEEL_EDGE);
        box(pose, materials, -0.13, 0.13, -1.47, 0.13, 0.34, -1.20, Material.GEAR);
        box(pose, materials, -0.46, 0.49, -1.00, -0.31, 0.65, -0.94, Material.AMBER);
        box(pose, materials, 0.31, 0.49, -1.00, 0.46, 0.65, -0.94, Material.AMBER);
    }

    private static void renderSurveyBooms(RailBeetleEntity beetle, PoseStack pose,
                                          MaterialBuffer materials, float phase) {
        float extension = beetle.mode() == BeetleMode.PLANNING || beetle.mode() == BeetleMode.READY ? 0.22f : 0.0f;
        extension += beetle.profile().surveyTier() * 0.09f;
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            pose.translate(side * 0.39, 0.76, -0.44);
            pose.mulPose(Axis.XP.rotationDegrees(1.5f * (float) Math.sin(phase * 0.6f)));
            box(pose, materials, -0.03, -0.02, -0.50 - extension, 0.03, 0.05, 0.34, Material.COPPER);
            box(pose, materials, -0.07, -0.04, -0.57 - extension, 0.07, 0.09,
                    -0.47 - extension, Material.AMBER);
            pose.popPose();
        }
    }

    private static void renderRunningGear(RailBeetleEntity beetle, PoseStack pose,
                                          MaterialBuffer materials, float phase) {
        Material brake = beetle.profile().brakeTier() > 0 ? Material.RED : Material.AGED_BRASS;
        float rodTravel = beetle.mode().moves() ? (float) Math.sin(phase * 2.0f) * 0.035f : 0;
        for (int side : new int[]{-1, 1}) {
            for (double z : new double[]{-0.72, 0.58}) {
                box(pose, materials, side < 0 ? -0.76 : 0.58, -0.03, z - 0.18,
                        side < 0 ? -0.58 : 0.76, 0.25, z + 0.18, Material.GEAR);
                box(pose, materials, side < 0 ? -0.81 : 0.72, 0.04, z - 0.10,
                        side < 0 ? -0.72 : 0.81, 0.20, z + 0.10, brake);
            }
            box(pose, materials, side < 0 ? -0.82 : 0.74, 0.08 + rodTravel, -0.75,
                    side < 0 ? -0.74 : 0.82, 0.14 + rodTravel, 0.62, Material.BRASS);
        }
    }

    private static void renderEngine(EngineKind kind, PoseStack pose, MaterialBuffer materials, float phase) {
        Material accent = switch (kind) {
            case STEAM -> Material.COPPER;
            case FLUX -> Material.RED;
            case SOURCE, SPIRIT -> Material.VIOLET;
            case LIFEFORCE -> Material.CRIMSON;
            case PRESSURE -> Material.CYAN;
            case SOUL -> Material.PALE;
            default -> Material.AMBER;
        };
        box(pose, materials, -0.505, 0.39, -0.63, -0.495, 0.63, 0.02, accent);
        box(pose, materials, 0.495, 0.39, -0.63, 0.505, 0.63, 0.02, accent);
        box(pose, materials, -0.28, 0.68, -0.48, 0.28, 0.76, -0.08, Material.VENT);
        if (kind == EngineKind.STEAM) {
            double pulse = 0.04 * Math.sin(phase);
            box(pose, materials, -0.11, 0.74, -0.70, 0.11, 1.02 + pulse, -0.50,
                    Material.GRILLE);
        } else if (kind == EngineKind.FLUX || kind == EngineKind.PRESSURE) {
            box(pose, materials, -0.58, 0.38, -0.48, -0.50, 0.62, -0.12, Material.BRASS);
            box(pose, materials, 0.50, 0.38, -0.48, 0.58, 0.62, -0.12, Material.BRASS);
        }
    }

    private static void renderModules(BeetleProfile p, PoseStack pose, MaterialBuffer materials, float phase) {
        if (p.governorTier() > 0) {
            pose.pushPose();
            pose.translate(0, 0.82, -0.22);
            pose.mulPose(Axis.YP.rotationDegrees(phase * (p.governorTier() == 2 ? 32.0f : 18.0f)));
            box(pose, materials, -0.23, -0.03, -0.035, 0.23, 0.03, 0.035, Material.GEAR);
            box(pose, materials, -0.035, -0.03, -0.23, 0.035, 0.03, 0.23, Material.GEAR);
            pose.popPose();
        }
        if (p.recuperatorTier() > 0) {
            box(pose, materials, -0.40, 0.76, 0.03, -0.27, 1.06, 0.17, Material.COPPER);
        }
        if (p.adhesionTier() > 0) {
            box(pose, materials, -0.69, 0.26, -0.83, -0.53, 0.45, -0.45, Material.AGED_BRASS);
            box(pose, materials, 0.53, 0.26, -0.83, 0.69, 0.45, -0.45, Material.AGED_BRASS);
        }
        if (p.torqueTier() > 0) {
            box(pose, materials, -0.38, 0.10, -0.18, 0.38, 0.25, 0.22, Material.GEAR);
        }
        if (p.drawgearTier() > 0) {
            box(pose, materials, -0.15, 0.13, 1.08, 0.15, 0.31, 1.36, Material.STEEL_EDGE);
        }
        if (p.remoteTier() > 0) {
            box(pose, materials, -0.04, 1.15, 0.58, 0.04, 1.38 + p.remoteTier() * 0.08,
                    0.66, Material.COPPER);
            box(pose, materials, -0.12, 1.36 + p.remoteTier() * 0.08, 0.55,
                    0.12, 1.42 + p.remoteTier() * 0.08, 0.69,
                    p.remoteTier() > 1 ? Material.VIOLET : Material.AMBER);
        }
        if (p.trestleTier() > 0) {
            box(pose, materials, -0.69, 0.29, 0.24, -0.56, 0.62, 0.92, Material.TIMBER);
            box(pose, materials, 0.56, 0.29, 0.24, 0.69, 0.62, 0.92, Material.TIMBER);
        }
    }

    private static void renderSearchlight(RailBeetleEntity beetle, PoseStack pose, MaterialBuffer materials) {
        if (!beetle.profile().searchlight()) return;
        box(pose, materials, -0.18, 0.45, -1.04, 0.18, 0.72, -0.92, Material.AGED_BRASS);
        box(pose, beetle.searchlightOn() ? materials.fullBright() : materials,
                -0.13, 0.50, -1.055, 0.13, 0.67, -1.045,
                beetle.searchlightOn() ? Material.AMBER : Material.STEEL_EDGE);
    }

    private static void box(PoseStack pose, MaterialBuffer materials,
                            double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ, Material material) {
        PoseStack.Pose last = pose.last();
        float x0 = (float) minX, y0 = (float) minY, z0 = (float) minZ;
        float x1 = (float) maxX, y1 = (float) maxY, z1 = (float) maxZ;
        float u0 = material.u0(), v0 = material.v0(), u1 = material.u1(), v1 = material.v1();

        face(last, materials, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0,
                u0, v0, u1, v1, 0, 0, -1);
        face(last, materials, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1,
                u0, v0, u1, v1, 0, 0, 1);
        face(last, materials, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1,
                u0, v0, u1, v1, -1, 0, 0);
        face(last, materials, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0,
                u0, v0, u1, v1, 1, 0, 0);
        face(last, materials, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
                u0, v0, u1, v1, 0, 1, 0);
        face(last, materials, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                u0, v0, u1, v1, 0, -1, 0);
    }

    private static void face(PoseStack.Pose pose, MaterialBuffer materials,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1,
                             float normalX, float normalY, float normalZ) {
        vertex(pose, materials, ax, ay, az, u0, v0, normalX, normalY, normalZ);
        vertex(pose, materials, bx, by, bz, u1, v0, normalX, normalY, normalZ);
        vertex(pose, materials, cx, cy, cz, u1, v1, normalX, normalY, normalZ);
        vertex(pose, materials, dx, dy, dz, u0, v1, normalX, normalY, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, MaterialBuffer materials,
                               float x, float y, float z, float u, float v,
                               float normalX, float normalY, float normalZ) {
        materials.consumer().vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(materials.packedLight())
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
    }

    private record MaterialBuffer(VertexConsumer consumer, int packedLight) {
        private MaterialBuffer fullBright() {
            return packedLight == LightTexture.FULL_BRIGHT
                    ? this
                    : new MaterialBuffer(consumer, LightTexture.FULL_BRIGHT);
        }
    }

    private enum Material {
        STEEL(0), STEEL_EDGE(1), AGED_BRASS(2), BRASS(3),
        COPPER(4), TIMBER(5), AMBER(6), RED(7),
        VIOLET(8), CYAN(9), PALE(10), CRIMSON(11),
        GRILLE(12), VENT(13), GEAR(14), TREAD(15);

        private static final float TILE = 0.25f;
        private static final float INSET = 0.5f / 64.0f;
        private final int index;

        Material(int index) {
            this.index = index;
        }

        private float u0() { return (index % 4) * TILE + INSET; }
        private float v0() { return (index / 4) * TILE + INSET; }
        private float u1() { return (index % 4 + 1) * TILE - INSET; }
        private float v1() { return (index / 4 + 1) * TILE - INSET; }
    }

    @Override public ResourceLocation getTextureLocation(RailBeetleEntity entity) { return TEXTURE; }
}
