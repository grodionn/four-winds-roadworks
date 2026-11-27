package dev.fourwinds.roads.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;

final class WaypostVertex {
    static void emit(VertexConsumer vertices, PoseStack.Pose pose, BakedQuad quad, float shade, int light, int overlay) {
        vertices.putBulkData(pose, quad, shade, shade, shade, light, overlay);
    }
}
