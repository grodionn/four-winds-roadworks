package dev.fourwinds.roads.mixin;

import com.mojang.datafixers.util.Either;
import dev.fourwinds.roads.RoadClearance;
import dev.fourwinds.roads.ShoreTerrain;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatus.class)
abstract class ChunkStatusTasksMixin {
    @Inject(method = "generate", at = @At("HEAD"))
    private void fourwinds$finishRoad(Executor executor, ServerLevel level, ChunkGenerator generator, StructureManager templates,
            ThreadedLevelLightEngine light, Function<?, ?> full, List<ChunkAccess> region, boolean force,
            CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> ci) {
        if ((Object)this == ChunkStatus.LIGHT) RoadClearance.beforeLighting(level, region.get(region.size() / 2));
    }

    @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
    private void fourwinds$survey(Executor executor, ServerLevel level, ChunkGenerator generator, StructureManager templates,
            ThreadedLevelLightEngine light, Function<?, ?> full, List<ChunkAccess> region, boolean force,
            CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> ci) {
        if ((Object)this == ChunkStatus.LIQUID_CARVERS) ci.setReturnValue(ci.getReturnValue().thenApply(result -> {
            result.left().ifPresent(chunk -> ShoreTerrain.capture(level, chunk));
            return result;
        }));
    }
}
