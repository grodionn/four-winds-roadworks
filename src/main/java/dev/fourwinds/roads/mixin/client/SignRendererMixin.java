package dev.fourwinds.roads.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fourwinds.roads.client.WaypostRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SignRenderer.class, remap = false)
abstract class SignRendererMixin {
    // Both vanilla and TFC pass their completed sign data through this method.
    @Inject(method = "renderSignWithText", at = @At("HEAD"), cancellable = true)
    private void fourwinds$renderWaypost(SignBlockEntity sign, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay, BlockState state, SignBlock block, WoodType wood, Model model, CallbackInfo ci) {
        if (WaypostRenderer.render(sign, pose, buffers, light, overlay)) ci.cancel();
    }

    @Inject(method = "getRenderBoundingBox(Lnet/minecraft/world/level/block/entity/SignBlockEntity;)Lnet/minecraft/world/phys/AABB;",
            at = @At("HEAD"), cancellable = true)
    private void fourwinds$includeArms(SignBlockEntity sign, CallbackInfoReturnable<AABB> ci) {
        if (WaypostRenderer.isWaypost(sign)) {
            var p = sign.getBlockPos();
            ci.setReturnValue(new AABB(p.getX()-1, p.getY(), p.getZ()-1, p.getX()+2, p.getY()+2, p.getZ()+2));
        }
    }
}
