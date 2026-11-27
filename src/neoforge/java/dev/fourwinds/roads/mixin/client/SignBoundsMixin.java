package dev.fourwinds.roads.mixin.client;

import dev.fourwinds.roads.client.WaypostRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SignRenderer.class, remap = false)
abstract class SignBoundsMixin {
    @Inject(method = "getRenderBoundingBox(Lnet/minecraft/world/level/block/entity/SignBlockEntity;)Lnet/minecraft/world/phys/AABB;",
            at = @At("HEAD"), cancellable = true)
    private void fourwinds$includeArms(SignBlockEntity sign, CallbackInfoReturnable<AABB> ci) {
        if (WaypostRenderer.isWaypost(sign)) {
            var p = sign.getBlockPos();
            ci.setReturnValue(new AABB(p.getX()-1, p.getY(), p.getZ()-1, p.getX()+2, p.getY()+2, p.getZ()+2));
        }
    }
}
