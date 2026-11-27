package dev.fourwinds.roads;

import java.util.BitSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

final class RoadData {
    static final ChunkField<CompoundTag> PENDING = tag("pending_clearance");
    static final ChunkField<CompoundTag> COLUMNS = tag("shore_before_features");
    static final ChunkField<CompoundTag> SURFACE = tag("surveyed_surface");
    static final ChunkField<BitSet> FOOTPRINT = new ChunkField<>("pier_footprint", () -> new BitSet(256), RoadApi.FOOTPRINT);
    static final List<ChunkField<?>> ALL = List.of(PENDING, COLUMNS, SURFACE, FOOTPRINT);

    private static ChunkField<CompoundTag> tag(String name) {
        return new ChunkField<>(name, CompoundTag::new, CompoundTag.CODEC);
    }
}
