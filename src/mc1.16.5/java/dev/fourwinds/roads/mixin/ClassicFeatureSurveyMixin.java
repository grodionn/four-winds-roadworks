package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.Roads116;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class ClassicFeatureSurveyMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void fourwinds$survey(WorldGenRegion region, StructureFeatureManager structures, CallbackInfo ci) {
        Roads116.survey(region);
    }
}
