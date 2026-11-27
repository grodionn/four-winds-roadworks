package dev.fourwinds.roads;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class RoadGame {
    public static MutableComponent translated(String key, Object... args) { return new net.minecraft.network.chat.TranslatableComponent(key, args); }
    public static MutableComponent literal(String text) { return new net.minecraft.network.chat.TextComponent(text); }
    public static String translationKey(Component text) {
        return text instanceof net.minecraft.network.chat.TranslatableComponent
                ? ((net.minecraft.network.chat.TranslatableComponent)text).getKey() : "";
    }
}
