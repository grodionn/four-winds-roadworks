package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.ForgeRoadStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class ForgeChunkPromotionMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("RETURN"))
    private void fourwinds$promote(ServerLevel level, ProtoChunk source, LevelChunk.PostLoadProcessor postLoad, CallbackInfo ci) {
        ForgeRoadStorage.fields((LevelChunk)(Object)this).putAll(ForgeRoadStorage.fields(source));
    }
}
