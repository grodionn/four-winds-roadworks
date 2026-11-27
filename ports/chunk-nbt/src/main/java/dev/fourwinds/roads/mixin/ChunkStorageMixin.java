package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.RoadChunkStorage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkAccess.class)
abstract class ChunkStorageMixin implements RoadChunkStorage {
    @Unique private final Map<String, Object> fourwinds$values = new ConcurrentHashMap<>(4);
    @Override public Map<String, Object> fourwinds$fields() { return fourwinds$values; }
}
