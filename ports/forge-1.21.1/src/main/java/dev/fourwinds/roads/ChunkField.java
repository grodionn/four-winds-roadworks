package dev.fourwinds.roads;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.chunk.ChunkAccess;

final class ChunkField<T> {
    private final String name;
    private final Supplier<T> factory;
    private final Codec<T> codec;

    ChunkField(String name, Supplier<T> factory, Codec<T> codec) {
        this.name = name;
        this.factory = factory;
        this.codec = codec;
    }

    @SuppressWarnings("unchecked")
    T get(ChunkAccess chunk) { return (T)ForgeRoadStorage.fields(chunk).computeIfAbsent(name, key -> factory.get()); }
    boolean has(ChunkAccess chunk) { return ForgeRoadStorage.fields(chunk).containsKey(name); }
    void set(ChunkAccess chunk, T value) { ForgeRoadStorage.fields(chunk).put(name, value); }
    void remove(ChunkAccess chunk) { ForgeRoadStorage.fields(chunk).remove(name); }

    static void load(ChunkAccess chunk, CompoundTag root) {
        var data = root.getCompound(FourWinds.ID);
        if (data.isEmpty()) return;
        for (var field : RoadData.ALL) field.decode(ForgeRoadStorage.fields(chunk), data);
    }

    private void decode(Map<String, Object> values, CompoundTag data) {
        var tag = data.get(name);
        if (tag != null) codec.parse(NbtOps.INSTANCE, tag).resultOrPartial(LogUtils.getLogger()::warn)
                .ifPresent(value -> values.put(name, value));
    }

    static void save(ChunkAccess chunk, CompoundTag root) {
        if (ForgeRoadStorage.fields(chunk).isEmpty()) return;
        var data = new CompoundTag();
        for (var field : RoadData.ALL) field.encode(chunk, data);
        if (!data.isEmpty()) root.put(FourWinds.ID, data);
    }

    private void encode(ChunkAccess chunk, CompoundTag data) {
        if (has(chunk)) data.put(name, codec.encodeStart(NbtOps.INSTANCE, get(chunk)).getOrThrow());
    }
}
