package dev.fourwinds.roads;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(FourWinds.ID)
public final class FourWinds {
    public static final String ID = "fourwindsroadworks";
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public FourWinds(IEventBus bus) {
        RoadClearance.register(bus);
        DeferredRegister<Feature<?>> features = DeferredRegister.create(Registries.FEATURE, ID);
        features.register("roads", RoadFeature::new);
        features.register(bus);
        DeferredRegister<MapCodec<? extends BiomeModifier>> modifiers =
                DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ID);
        modifiers.register("overworld_roads", () -> RoadsModifier.CODEC);
        modifiers.register(bus);
    }

    private record RoadsModifier(Holder<PlacedFeature> feature) implements BiomeModifier {
        static final MapCodec<RoadsModifier> CODEC = PlacedFeature.CODEC.fieldOf("feature")
                .xmap(RoadsModifier::new, RoadsModifier::feature);

        @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            // TFC reads a flattened list from its own placed-feature tags.
            if (phase == Phase.ADD && biome.is(BiomeTags.IS_OVERWORLD)
                    && !biome.unwrapKey().orElseThrow().location().getNamespace().equals("tfc")) {
                builder.getGenerationSettings().addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, feature);
            }
        }
        @Override public MapCodec<? extends BiomeModifier> codec() { return CODEC; }
    }
}
