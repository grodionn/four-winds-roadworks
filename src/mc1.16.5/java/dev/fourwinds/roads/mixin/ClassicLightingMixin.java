package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.Roads116;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatus.class)
abstract class ClassicLightingMixin {
    @Inject(method = "generate", at = @At("HEAD"))
    private void fourwinds$finish(ServerLevel level, ChunkGenerator generator, StructureManager templates,
            ThreadedLevelLightEngine light, Function<?, ?> full, List<ChunkAccess> chunks, CallbackInfoReturnable<?> ci) {
        if ((Object)this == ChunkStatus.LIGHT) Roads116.beforeLight(level, generator, chunks);
    }
}
