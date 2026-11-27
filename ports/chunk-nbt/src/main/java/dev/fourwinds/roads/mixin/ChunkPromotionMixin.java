package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.RoadChunkStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class ChunkPromotionMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("RETURN"))
    private void fourwinds$promote(ServerLevel level, ProtoChunk source, LevelChunk.PostLoadProcessor postLoad, CallbackInfo ci) {
        RoadChunkStorage.fields((LevelChunk)(Object)this).putAll(RoadChunkStorage.fields(source));
    }
}
