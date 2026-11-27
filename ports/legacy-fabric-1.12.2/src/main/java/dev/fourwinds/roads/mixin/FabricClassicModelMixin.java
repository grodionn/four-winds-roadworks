package dev.fourwinds.roads.mixin;

import dev.fourwinds.roads.client.FabricClassicSigns;
import java.io.IOException;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.client.render.model.json.BlockModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelLoader.class)
abstract class FabricClassicModelMixin {
    @Invoker("getModel") abstract BlockModel fourwinds$load(Identifier id) throws IOException;
    @Invoker("method_10386") abstract BakedModel fourwinds$bake(BlockModel model, ModelRotation rotation, boolean uvLock);

    @Inject(method = "method_10383", at = @At("RETURN"))
    private void fourwinds$bakeWayposts(CallbackInfoReturnable<Registry<ModelIdentifier, BakedModel>> ci) throws IOException {
        FabricClassicSigns.crossing = fourwinds$model("crossroads_waypost");
        FabricClassicSigns.village = fourwinds$model("village_waypost");
    }

    @Unique private BakedModel fourwinds$model(String model) throws IOException {
        BlockModel drawing = fourwinds$load(new Identifier("fourwindsroadworks", "block/" + model));
        return fourwinds$bake(drawing, ModelRotation.X0_Y0, false);
    }
}
