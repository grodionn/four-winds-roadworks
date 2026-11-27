package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.FabricClassicWorld;
import net.minecraft.server.world.ChunkGenerator;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(World.class)
abstract class FabricClassicWorldMixin implements FabricClassicWorld.Owner {
    @Unique private FabricClassicWorld fourwinds$roads;
    @Unique private boolean fourwinds$checked;
    @Override public void fourwinds$populate(ChunkGenerator generator,int cx,int cz) {
        if (!fourwinds$checked) {
            fourwinds$roads=FabricClassicWorld.create((World)(Object)this,generator);
            fourwinds$checked=true;
        }
        if (fourwinds$roads!=null) fourwinds$roads.generate(cx,cz);
    }
}
