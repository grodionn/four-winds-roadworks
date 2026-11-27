package dev.fourwinds.roads;

import java.util.Map;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;

public interface RoadChunkStorage {
    Map<String, Object> fourwinds$fields();

    static Map<String, Object> fields(ChunkAccess chunk) {
        if (chunk instanceof ImposterProtoChunk view) chunk = view.getWrapped();
        return ((RoadChunkStorage)chunk).fourwinds$fields();
    }
}
