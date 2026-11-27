package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.Roads116;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
abstract class ClassicSurveyNbtMixin {
    @Inject(method = "read", at = @At("RETURN"))
    private static void fourwinds$load(ServerLevel level, StructureManager templates, PoiManager poi, ChunkPos pos, CompoundTag root, CallbackInfoReturnable<ProtoChunk> ci) {
        if (root.contains("fourwindsroadworks:survey",10)) ((Roads116.Pending)ci.getReturnValue()).fourwinds$setSurvey(root.getCompound("fourwindsroadworks:survey"));
    }
    @Inject(method = "write", at = @At("RETURN"))
    private static void fourwinds$save(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> ci) {
        if (chunk instanceof Roads116.Pending) {
            CompoundTag survey = ((Roads116.Pending)chunk).fourwinds$getSurvey();
            if (survey != null) ci.getReturnValue().put("fourwindsroadworks:survey", survey);
        }
    }
}
