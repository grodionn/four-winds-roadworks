package dev.fourwinds.roads;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.level.chunk.ChunkAccess;

final class ChunkField<T> {
    private final String name;
    private final Supplier<T> factory;
    private final Codec<T> codec;
    private AttachmentType<T> type;

    ChunkField(String name, Supplier<T> factory, Codec<T> codec) {
        this.name = name;
        this.factory = factory;
        this.codec = codec;
    }

    void register() {
        type = AttachmentRegistry.createPersistent(FourWinds.id(name), codec);
    }

    T get(ChunkAccess chunk) { return ((AttachmentTarget)chunk).getAttachedOrCreate(type, factory); }
    boolean has(ChunkAccess chunk) { return ((AttachmentTarget)chunk).hasAttached(type); }
    void set(ChunkAccess chunk, T value) { ((AttachmentTarget)chunk).setAttached(type, value); }
    void remove(ChunkAccess chunk) { ((AttachmentTarget)chunk).removeAttached(type); }
}
