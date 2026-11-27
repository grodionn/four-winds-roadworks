package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.ChunkField;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
abstract class FabricChunkSerializerMixin {
    @Inject(method = "read", at = @At("RETURN"))
    private static void fourwinds$load(ServerLevel level, PoiManager poi, ChunkPos pos, CompoundTag data,
            CallbackInfoReturnable<ProtoChunk> ci) {
        ChunkField.load(ci.getReturnValue(), data);
    }
    @Inject(method = "write", at = @At("RETURN"))
    private static void fourwinds$save(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> ci) {
        ChunkField.save(chunk, ci.getReturnValue());
    }
}
