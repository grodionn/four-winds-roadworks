package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.client.FabricClassicSigns;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignBlockEntityRenderer.class)
abstract class FabricClassicSignMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;DDDFIF)V", at = @At("HEAD"), cancellable = true)
    private void fourwinds$draw(SignBlockEntity sign, double x, double y, double z, float tickDelta,
            int breakingStage, float alpha, CallbackInfo ci) {
        if (FabricClassicSigns.render(sign, x, y, z)) ci.cancel();
    }
}
