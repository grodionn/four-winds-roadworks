package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.Roads116;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ProtoChunk.class)
abstract class ClassicSurveyStorageMixin implements Roads116.Pending {
    @Unique private CompoundTag fourwinds$survey;
    @Override public CompoundTag fourwinds$getSurvey() { return fourwinds$survey; }
    @Override public void fourwinds$setSurvey(CompoundTag value) { fourwinds$survey = value; }
}
