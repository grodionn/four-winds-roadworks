package dev.fourwinds.roads;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

final class ChunkField<T> {
    private final String name;
    private final AttachmentType<T> type;

    ChunkField(String name, Supplier<T> factory, Codec<T> codec) {
        this.name = name;
        this.type = AttachmentType.builder(factory).serialize(codec).build();
    }

    static void register(IEventBus bus) {
        var registry = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FourWinds.ID);
        for (var field : RoadData.ALL) registry.register(field.name, () -> field.type);
        registry.register(bus);
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel) RoadClearance.discardLegacy(event.getChunk());
        });
    }

    T get(ChunkAccess chunk) { return chunk.getData(type); }
    boolean has(ChunkAccess chunk) { return chunk.hasData(type); }
    void set(ChunkAccess chunk, T value) { chunk.setData(type, value); }
    void remove(ChunkAccess chunk) { chunk.removeData(type); }
}
