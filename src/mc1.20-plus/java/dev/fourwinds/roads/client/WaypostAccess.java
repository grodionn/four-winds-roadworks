package dev.fourwinds.roads.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.fourwinds.roads.RoadGame;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class WaypostAccess {
    static Function<Direction, List<BakedQuad>> faces(BakedModel model, BlockState state, long seed) {
        var random = RoadGame.random(seed);
        return side -> {
            random.setSeed(seed);
            return model.getQuads(state, side, random);
        };
    }
    static Component line(SignBlockEntity sign, int i) { return sign.getFrontText().getMessage(i, false); }
    static void rotate(PoseStack pose, float degrees) { pose.mulPose(Axis.YP.rotationDegrees(degrees)); }
    static void draw(Font font, Component label, float x, PoseStack pose, MultiBufferSource buffers, int light) {
        font.drawInBatch(label, x, -3.7f, 0xff241b12, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
    }
}
