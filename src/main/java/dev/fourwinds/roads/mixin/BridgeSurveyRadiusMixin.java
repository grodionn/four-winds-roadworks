package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.ShoreTerrain;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ChunkStep.Builder.class,remap=false)
abstract class BridgeSurveyRadiusMixin {
    @Shadow @Final private ChunkStatus status;
    @Shadow public abstract ChunkStep.Builder addRequirement(ChunkStatus dependency,int radius);

    // Read both banks before decorating. The existing one-chunk block write radius stays in force.
    @Inject(method="blockStateWriteRadius",at=@At("HEAD"))
    private void fourwinds$surveyBanks(int radius,CallbackInfoReturnable<ChunkStep.Builder> ci) {
        if (status==ChunkStatus.FEATURES && radius==1) addRequirement(ChunkStatus.CARVERS,ShoreTerrain.SURVEY_RADIUS);
    }
}
