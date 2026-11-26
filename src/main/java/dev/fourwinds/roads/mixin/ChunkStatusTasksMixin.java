package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.RoadClearance;
import dev.fourwinds.roads.ShoreTerrain;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkStatusTasks.class, remap = false)
abstract class ChunkStatusTasksMixin {
    @Inject(method = "generateCarvers", at = @At("RETURN"))
    private static void fourwinds$rememberShore(WorldGenContext context, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> neighbours, ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        ShoreTerrain.capture(context.level(),chunk);
    }

    // LIGHT requires every adjacent chunk to have passed FEATURES and INITIALIZE_LIGHT.
    @Inject(method = "light", at = @At("HEAD"))
    private static void fourwinds$finishRoad(WorldGenContext context, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> neighbours, ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        RoadClearance.beforeLighting(context.level(),chunk);
    }
}
