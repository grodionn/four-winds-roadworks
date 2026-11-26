package dev.fourwinds.roads.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.fourwinds.roads.FourWinds;
import java.util.List;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = FourWinds.ID, value = Dist.CLIENT)
public final class WaypostRenderer {
    private static final String PREFIX = "sign.fourwindsroadworks.";
    private record TimberModels(ModelResourceLocation crossing, ModelResourceLocation village) {
        TimberModels(String folder) { this(model(folder+"crossroads_waypost"),model(folder+"village_waypost")); }
        void register(ModelEvent.RegisterAdditional event) { event.register(crossing); event.register(village); }
    }
    private static final TimberModels OAK = new TimberModels("");
    private static final TimberModels TFC_OAK = new TimberModels("tfc/");
    // Baked models may put quads in a directional list or in the final, unculled list.
    private static final Direction[] QUAD_SIDES = Arrays.copyOf(Direction.values(), Direction.values().length + 1);
    private record Arm(Direction direction, float height, Component label) {
        Arm(Direction direction, float height) {
            this(direction, height, Component.translatable(PREFIX + direction.getName()));
        }
    }
    // Upper and lower mortises in the Blockbench model, in sixteenths of a block.
    private static final List<Arm> ARMS = List.of(new Arm(Direction.EAST, 20.5f), new Arm(Direction.WEST, 19.5f),
            new Arm(Direction.NORTH, 12.5f), new Arm(Direction.SOUTH, 13.5f));

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(FourWinds.id("block/" + name));
    }

    @SubscribeEvent
    private static void registerModels(ModelEvent.RegisterAdditional event) {
        OAK.register(event);
        if (ModList.get().isLoaded("tfc")) TFC_OAK.register(event);
    }

    private static String key(Component text) {
        return text.getContents() instanceof TranslatableContents tr ? tr.getKey() : "";
    }

    public static boolean isWaypost(SignBlockEntity sign) {
        if (!(sign.getBlockState().getBlock() instanceof StandingSignBlock)) return false;
        return switch (key(sign.getFrontText().getMessage(0, false))) {
            case PREFIX + "crossing", PREFIX + "village", PREFIX + "pixies" -> true;
            default -> false;
        };
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
        var text = sign.getFrontText();
        Component title = text.getMessage(0, false);
        boolean crossing = key(title).equals(PREFIX + "crossing");
        Direction toward = crossing ? Direction.EAST : bearing(text.getMessage(1, false));
        if (toward == null || !toward.getAxis().isHorizontal()) return false;
        var mc = Minecraft.getInstance();
        pose.pushPose();
        turn(pose, toward);
        var wood = BuiltInRegistries.BLOCK.getKey(sign.getBlockState().getBlock()).getNamespace().equals("tfc") ? TFC_OAK : OAK;
        var baked = mc.getModelManager().getModel(crossing ? wood.crossing : wood.village);
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
            lettering(pose, buffers, light, Component.translatable(PREFIX + toward.getName()),
                    18.75f, 17.9f, 0.01171875f, 0.9f);
            lettering(pose, buffers, light, text.getMessage(2, false), 19.5f, 10f, 0.015625f, 0.82f);
        }
        pose.popPose();
        return true;
    }

    private static void renderTimber(SignBlockEntity sign, BakedModel model, Direction toward, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        Rotation yaw = switch (toward) {
            case NORTH -> Rotation.COUNTERCLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_180;
            case SOUTH -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
        var layer = RenderType.solid();
        var vertices = buffers.getBuffer(layer);
        long seed = sign.getBlockPos().asLong();
        var random = RandomSource.create(seed);
        for (Direction side : QUAD_SIDES) {
            random.setSeed(seed);
            for (var quad : model.getQuads(sign.getBlockState(), side, random, ModelData.EMPTY, null)) {
                float shade = sign.getLevel().getShade(yaw.rotate(quad.getDirection()), quad.isShade());
                vertices.putBulkData(pose.last(), quad, shade, shade, shade, 1, light, overlay);
            }
        }
    }

    private static void turn(PoseStack pose, Direction bearing) {
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(270 - bearing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
    }

    private static void lettering(PoseStack pose, MultiBufferSource buffers, int light, Component label,
            float x, float y, float maxScale, float width) {
        var font = Minecraft.getInstance().font;
        int pixels = font.width(label);
        if (pixels == 0) return;
        float scale = Math.min(maxScale, width / pixels);
        pose.pushPose();
        pose.translate(x / 16f, y / 16f, 11 / 16f);
        for (int face = 0; face < 2; face++) {
            pose.pushPose();
            if (face == 1) pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(0, 0, 1 / 16f + 0.002f);
            pose.scale(scale, -scale, scale);
            font.drawInBatch(label, -pixels / 2f, -3.7f, 0xff241b12, false, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, light);
            pose.popPose();
        }
        pose.popPose();
    }
}
