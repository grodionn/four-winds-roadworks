package dev.fourwinds.roads.client;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.lwjgl.opengl.GL11;

public final class FabricClassicSigns {
    public static BakedModel crossing, village;
    private static final String PREFIX = "sign.fourwindsroadworks.";
    private static final String[] ARMS = {"east", "west", "north", "south"};
    private static final int[] ROTATION = {0, 180, 90, 270};
    private static final float[] HEIGHT = {20.5f, 19.5f, 12.5f, 13.5f};

    private static String key(Text text) {
        return text instanceof TranslatableText ? ((TranslatableText) text).getKey() : "";
    }

    public static boolean render(SignBlockEntity sign, double x, double y, double z) {
        String name = key(sign.text[0]);
        boolean crossroads = (PREFIX + "crossing").equals(name);
        if (!crossroads && !(PREFIX + "village").equals(name)) return false;
        BakedModel model = crossroads ? crossing : village;
        if (model == null) return false;
        int arm = 0;
        if (!crossroads) {
            while (arm < ARMS.length && !(PREFIX + ARMS[arm]).equals(key(sign.text[1]))) arm++;
            if (arm == ARMS.length) return false;
        }
        MinecraftClient game = MinecraftClient.getInstance();
        boolean lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            turn(ROTATION[arm]);
            game.getTextureManager().bindTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX);
            game.getBlockRenderManager().getModelRenderer().method_12350(model, 1f, 1f, 1f, 1f);
            GlStateManager.disableLighting();
            if (crossroads) {
                for (int i = 0; i < ARMS.length; i++) {
                    GlStateManager.pushMatrix();
                    turn(ROTATION[i]);
                    label(new TranslatableText(PREFIX + ARMS[i]), 18.5f, HEIGHT[i], .01953125f, .88f);
                    GlStateManager.popMatrix();
                }
            } else {
                label(sign.text[0], 18.75f, 20.85f, .015625f, .96f);
                label(sign.text[1], 18.75f, 17.9f, .01171875f, .90f);
                label(sign.text[2], 19.5f, 10f, .015625f, .82f);
            }
        } finally {
            if (lit) GlStateManager.enableLighting();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }
        return true;
    }

    private static void turn(float yaw) {
        GlStateManager.translate(.5, 0, .5);
        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.translate(-.5, 0, -.5);
    }

    private static void label(Text text, float x, float y, float scale, float maxWidth) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        String value = text.asUnformattedString();
        int width = font.getStringWidth(value);
        if (width == 0) return;
        float zoom = Math.min(scale, maxWidth / width);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x / 16, y / 16, 11f / 16);
        for (int side = 0; side < 2; side++) {
            GlStateManager.pushMatrix();
            GlStateManager.rotate(side * 180, 0, 1, 0);
            GlStateManager.translate(0, 0, 1.0 / 16 + .002);
            GlStateManager.scale(zoom, -zoom, zoom);
            font.draw(value, -width / 2f, -4, 0xff332215, false);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
}
