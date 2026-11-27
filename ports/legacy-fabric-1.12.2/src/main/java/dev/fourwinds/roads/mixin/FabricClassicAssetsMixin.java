package dev.fourwinds.roads.mixin;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.resource.DefaultResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DefaultResourcePack.class)
abstract class FabricClassicAssetsMixin {
    // The 1.12 built-in pack reads classpath assets, but indexes only its known namespaces.
    @Inject(method = "getNamespaces", at = @At("RETURN"), cancellable = true)
    private void fourwinds$registerModelsAndLanguage(CallbackInfoReturnable<Set<String>> ci) {
        ci.setReturnValue(ImmutableSet.<String>builder().addAll(ci.getReturnValue()).add("fourwindsroadworks").build());
    }
}
