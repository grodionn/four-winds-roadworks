package dev.fourwinds.roads.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.fourwinds.roads.RoadApi;
import dev.fourwinds.roads.RoadGame;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public final class WaypostRenderer {
    private static final String PREFIX = "sign.fourwindsroadworks.";
    // Baked models may put quads in a directional list or in the final, unculled list.
    private static final Direction[] QUAD_SIDES = Arrays.copyOf(Direction.values(), Direction.values().length + 1);
    private static final class Arm {
        final Direction direction;
        final float height;
        final Component label;

        Arm(Direction direction, float height) {
            this.direction = direction;
            this.height = height;
            this.label = RoadGame.translated(PREFIX + direction.getName());
        }
    }
    // Upper and lower mortises in the Blockbench model, in sixteenths of a block.
    private static final List<Arm> ARMS = Arrays.asList(new Arm(Direction.EAST, 20.5f), new Arm(Direction.WEST, 19.5f),
            new Arm(Direction.NORTH, 12.5f), new Arm(Direction.SOUTH, 13.5f));

    private static String key(Component text) {
        return RoadGame.translationKey(text);
    }

    public static boolean isWaypost(SignBlockEntity sign) {
        if (!(sign.getBlockState().getBlock() instanceof StandingSignBlock)) return false;
        String name = key(WaypostAccess.line(sign, 0));
        return name.equals(PREFIX + "crossing") || name.equals(PREFIX + "village") || name.equals(PREFIX + "pixies");
    }

    // Saved signs encode the bearing as a translation either before or after a literal arrow.
    private static Direction bearing(Component line) {
        String key = key(line);
        if (key.startsWith(PREFIX)) return Direction.byName(key.substring(PREFIX.length()));
        for (Component part : line.getSiblings()) {
            Direction dir = bearing(part);
            if (dir != null) return dir;
        }
        return null;
    }

    public static boolean render(SignBlockEntity sign, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!isWaypost(sign) || sign.getLevel() == null) return false;
        Component title = WaypostAccess.line(sign, 0);
        boolean crossing = key(title).equals(PREFIX + "crossing");
        Direction toward = crossing ? Direction.EAST : bearing(WaypostAccess.line(sign, 1));
        if (toward == null || !toward.getAxis().isHorizontal()) return false;
        pose.pushPose();
        turn(pose, toward);
        boolean tfc = RoadApi.blockId(sign.getBlockState().getBlock()).getNamespace().equals("tfc");
        BakedModel baked = WaypostModels.get(tfc, crossing);
        renderTimber(sign, baked, toward, pose, buffers, light, overlay);
        if (crossing) {
            for (Arm arm : ARMS) {
                pose.pushPose();
                turn(pose, arm.direction);
                lettering(pose, buffers, light, arm.label, 18.5f, arm.height, 0.01953125f, 0.88f);
                pose.popPose();
            }
        } else {
            lettering(pose, buffers, light, title, 18.75f, 20.85f, 0.015625f, 0.96f);
            lettering(pose, buffers, light, RoadGame.translated(PREFIX + toward.getName()),
                    18.75f, 17.9f, 0.01171875f, 0.9f);
            lettering(pose, buffers, light, WaypostAccess.line(sign, 2), 19.5f, 10f, 0.015625f, 0.82f);
        }
        pose.popPose();
        return true;
    }

    private static void renderTimber(SignBlockEntity sign, BakedModel model, Direction toward, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        Rotation yaw;
        switch (toward) {
            case NORTH: yaw = Rotation.COUNTERCLOCKWISE_90; break;
            case WEST: yaw = Rotation.CLOCKWISE_180; break;
            case SOUTH: yaw = Rotation.CLOCKWISE_90; break;
            default: yaw = Rotation.NONE;
        }
        VertexConsumer vertices = buffers.getBuffer(RenderType.solid());
        long seed = sign.getBlockPos().asLong();
        Function<Direction, List<BakedQuad>> faces = WaypostAccess.faces(model, sign.getBlockState(), seed);
        for (Direction side : QUAD_SIDES) {
            for (BakedQuad quad : faces.apply(side)) {
                float shade = sign.getLevel().getShade(yaw.rotate(quad.getDirection()), quad.isShade());
                WaypostVertex.emit(vertices, pose.last(), quad, shade, light, overlay);
            }
        }
    }

    private static void turn(PoseStack pose, Direction bearing) {
        pose.translate(0.5, 0, 0.5);
        WaypostAccess.rotate(pose, 270 - bearing.toYRot());
        pose.translate(-0.5, 0, -0.5);
    }

    private static void lettering(PoseStack pose, MultiBufferSource buffers, int light, Component label,
            float x, float y, float maxScale, float width) {
        Font font = Minecraft.getInstance().font;
        int pixels = font.width(label);
        if (pixels == 0) return;
        float scale = Math.min(maxScale, width / pixels);
        pose.pushPose();
        pose.translate(x / 16f, y / 16f, 11 / 16f);
        for (int face = 0; face < 2; face++) {
            pose.pushPose();
            if (face == 1) WaypostAccess.rotate(pose, 180);
            pose.translate(0, 0, 1 / 16f + 0.002f);
            pose.scale(scale, -scale, scale);
            WaypostAccess.draw(font, label, -pixels / 2f, pose, buffers, light);
            pose.popPose();
        }
        pose.popPose();
    }
}
