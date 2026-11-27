package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.ShoreTerrain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatus.class)
abstract class BridgeSurveyRadiusMixin {
    @Shadow @Final @Mutable private static List<ChunkStatus> STATUS_BY_RANGE;

    // 1.20 derives its chunk ticket distances from this list during static initialization.
    @Inject(method = "<clinit>", at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC,
            target = "Lnet/minecraft/world/level/chunk/ChunkStatus;STATUS_BY_RANGE:Ljava/util/List;", shift = At.Shift.AFTER))
    private static void fourwinds$surveyTickets(CallbackInfo ci) {
        var distances = new ArrayList<>(STATUS_BY_RANGE);
        distances.addAll(distances.indexOf(ChunkStatus.CARVERS), Collections.nCopies(ShoreTerrain.SURVEY_RADIUS - 1, ChunkStatus.CARVERS));
        STATUS_BY_RANGE = List.copyOf(distances);
    }

    @Inject(method = "getRange", at = @At("RETURN"), cancellable = true)
    private void fourwinds$surveyRadius(CallbackInfoReturnable<Integer> ci) {
        if ((Object)this == ChunkStatus.FEATURES) ci.setReturnValue(Math.max(ci.getReturnValueI(), ShoreTerrain.SURVEY_RADIUS));
    }
}
