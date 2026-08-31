package com.bettercontent.railscout.client;

import com.bettercontent.railscout.entity.RailScoutEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.core.Direction;

/** Vanilla minecart renderer with a tiny amber marker on its semantic nose. */
public final class RailScoutRenderer extends MinecartRenderer<RailScoutEntity> {
    public RailScoutRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.MINECART);
    }

    @Override
    public void render(RailScoutEntity scout, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        super.render(scout, yaw, partialTick, pose, buffers, packedLight);
        Direction nose = scout.noseHeading();
        double x = nose.getStepX() * 0.72;
        double z = nose.getStepZ() * 0.72;
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugFilledBox());
        LevelRenderer.addChainedFilledBoxVertices(pose, consumer,
                x - 0.10, 0.28, z - 0.10, x + 0.10, 0.48, z + 0.10,
                1.0f, 0.55f, 0.08f, 1.0f);
    }
}
