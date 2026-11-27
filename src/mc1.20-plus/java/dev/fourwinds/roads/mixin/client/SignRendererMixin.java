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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignRenderer.class)
abstract class SignRendererMixin {
    // Both vanilla and TFC pass their completed sign data through this method.
    @Inject(method = "renderSignWithText", at = @At("HEAD"), cancellable = true)
    private void fourwinds$renderWaypost(SignBlockEntity sign, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay, BlockState state, SignBlock block, WoodType wood, Model model, CallbackInfo ci) {
        if (WaypostRenderer.render(sign, pose, buffers, light, overlay)) ci.cancel();
    }
}
