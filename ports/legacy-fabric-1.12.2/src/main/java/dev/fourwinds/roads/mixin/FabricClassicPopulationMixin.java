package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.FabricClassicWorld;
import net.minecraft.server.world.ChunkGenerator;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chunk.class)
abstract class FabricClassicPopulationMixin {
    @Inject(method = "populate(Lnet/minecraft/server/world/ChunkGenerator;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ChunkGenerator;populate(II)V", shift = At.Shift.AFTER))
    private void fourwinds$afterDecoration(ChunkGenerator generator, CallbackInfo ci) {
        Chunk chunk=(Chunk)(Object)this;
        ((FabricClassicWorld.Owner)chunk.getWorld()).fourwinds$populate(generator,chunk.chunkX,chunk.chunkZ);
    }
}
