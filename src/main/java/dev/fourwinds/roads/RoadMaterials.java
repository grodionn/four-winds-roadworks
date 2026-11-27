package dev.fourwinds.roads;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class RoadMaterials {
    private static final RoadMaterials VANILLA = new RoadMaterials();
    private static final Map<String,RoadMaterials> TFC = new ConcurrentHashMap<>();
    private final Map<Block,Block> blocks = new IdentityHashMap<>();

    static boolean isTfc(net.minecraft.server.level.ServerLevel level) {
        return level.getChunkSource().getGenerator().getTypeNameForDataFixer().map(k -> k.location().getNamespace().equals("tfc")).orElse(false);
    }

    static String quarry(BlockState state) {
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path=id.getPath();
        return id.getNamespace().equals("tfc") && (path.startsWith("rock/raw/") || path.startsWith("rock/hardened/")
                || path.startsWith("rock/gravel/")) ? path.substring(path.lastIndexOf('/')+1) : null;
    }

    RoadMaterials construction(String rock) {
        return construction(this!=VANILLA,rock);
    }
    static RoadMaterials construction(boolean tfc,String rock) {
        return tfc?TFC.computeIfAbsent(rock+"/"+RoadVersion.DEFAULT_SOIL+"/yellow",k->new RoadMaterials(rock,RoadVersion.DEFAULT_SOIL,"yellow")):VANILLA;
    }

    static RoadMaterials forChunk(WorldGenRegion region) {
        if (!isTfc(region.getLevel())) return VANILLA;
        var c = region.getCenter();
        int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
        int top = region.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
        String rock = "granite", soil = RoadVersion.DEFAULT_SOIL, sand = "yellow";
        var p = new BlockPos.MutableBlockPos(x,top,z);
        for (int y=top; y>=Math.max(region.getMinBuildHeight(),top-95); y--) {
            var id = BuiltInRegistries.BLOCK.getKey(region.getBlockState(p.setY(y)).getBlock());
            if (!id.getNamespace().equals("tfc")) continue;
            String path = id.getPath(), last = path.substring(path.lastIndexOf('/')+1);
            if (path.startsWith("grass/") || path.startsWith("dirt/")) soil=last;
            else if (path.startsWith("sand/")) sand=last;
            else if (path.startsWith("rock/raw/") || path.startsWith("rock/hardened/") || path.startsWith("rock/gravel/")) {
                rock=last; break;
            }
        }
        String key=rock+"/"+soil+"/"+sand;
        final String r=rock, s=soil, color=sand;
        return TFC.computeIfAbsent(key,k -> new RoadMaterials(r,s,color));
    }

    private RoadMaterials() {}
    private RoadMaterials(String rock, String soil, String sand) {
        bind(Blocks.COBBLESTONE,"rock/cobble/"+rock);
        bind(Blocks.MOSSY_COBBLESTONE,"rock/mossy_cobble/"+rock);
        bind(Blocks.COBBLESTONE_WALL,"rock/cobble/"+rock+"_wall");
        bind(Blocks.COBBLESTONE_SLAB,"rock/cobble/"+rock+"_slab");
        bind(Blocks.MOSSY_COBBLESTONE_SLAB,"rock/mossy_cobble/"+rock+"_slab");
        bind(Blocks.MOSSY_COBBLESTONE_WALL,"rock/mossy_cobble/"+rock+"_wall");
        bind(Blocks.STONE_BUTTON,"rock/mossy_loose/"+rock);
        bind(Blocks.ANDESITE,"rock/smooth/"+rock);
        bind(Blocks.POLISHED_ANDESITE,"rock/smooth/"+rock);
        bind(Blocks.TUFF,"rock/cracked_bricks/"+rock);
        bind(Blocks.STONE_BRICKS,"rock/bricks/"+rock);
        bind(Blocks.MOSSY_STONE_BRICKS,"rock/mossy_bricks/"+rock);
        bind(Blocks.CHISELED_STONE_BRICKS,"rock/chiseled/"+rock);
        bind(Blocks.STONE_BRICK_WALL,"rock/bricks/"+rock+"_wall");
        bind(Blocks.STONE_BRICK_SLAB,"rock/bricks/"+rock+"_slab");
        bind(Blocks.STONE_BRICK_STAIRS,"rock/bricks/"+rock+"_stairs");
        bind(Blocks.COBBLESTONE_STAIRS,"rock/cobble/"+rock+"_stairs");
        bind(Blocks.GRAVEL,"rock/gravel/"+rock);
        bind(Blocks.DIRT_PATH,"grass_path/"+soil);
        bind(Blocks.COARSE_DIRT,"coarse_dirt/"+soil);
        bind(Blocks.MUD_BRICK_SLAB,"mud_bricks/"+soil+"_slab");
        bind(Blocks.MUD_BRICK_STAIRS,"mud_bricks/"+soil+"_stairs");
        bind(Blocks.SAND,"sand/"+sand);
        bind(Blocks.RED_SAND,"sand/"+sand);
        for (Block b : new Block[]{Blocks.SANDSTONE,Blocks.RED_SANDSTONE}) bind(b,"raw_sandstone/"+sand);
        for (Block b : new Block[]{Blocks.CUT_SANDSTONE,Blocks.CUT_RED_SANDSTONE}) bind(b,"cut_sandstone/"+sand);
        bind(Blocks.SANDSTONE_SLAB,"raw_sandstone/"+sand+"_slab");
        bind(Blocks.SANDSTONE_STAIRS,"raw_sandstone/"+sand+"_stairs");
        bind(Blocks.OAK_PLANKS,"wood/planks/oak");
        bind(Blocks.SPRUCE_PLANKS,"wood/planks/spruce");
        bind(Blocks.DARK_OAK_PLANKS,"wood/planks/chestnut");
        bind(Blocks.SPRUCE_STAIRS,"wood/planks/spruce_stairs");
        bind(Blocks.SPRUCE_SLAB,"wood/planks/spruce_slab");
        bind(Blocks.DARK_OAK_STAIRS,"wood/planks/chestnut_stairs");
        bind(Blocks.DARK_OAK_SLAB,"wood/planks/chestnut_slab");
        bind(Blocks.OAK_LOG,"wood/log/oak");
        bind(Blocks.CAMPFIRE,"firepit");
        bind(Blocks.STRIPPED_SPRUCE_LOG,"wood/stripped_log/spruce");
        bind(Blocks.SPRUCE_FENCE,"wood/fence/spruce");
        bind(Blocks.OAK_FENCE,"wood/fence/oak");
        bind(Blocks.OAK_SIGN,"wood/sign/oak");
    }

    boolean usesLanterns() { return this == VANILLA; }

    String localSoil(BlockState ground) { return this==VANILLA?null:soil(ground); }

    static String soil(BlockState ground) {
        var id=BuiltInRegistries.BLOCK.getKey(ground.getBlock());
        if (!id.getNamespace().equals("tfc")) return null;
        String path=id.getPath();
        int split=path.indexOf('/');
        if (split<0) return null;
        return switch (path.substring(0,split)) {
            case "grass","duff","dirt","coarse_dirt","grass_path","rooted_dirt","mud" -> path.substring(split+1);
            default -> null;
        };
    }

    static BlockState withSoil(BlockState state,String soil) {
        if (soil==null) return state;
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path=id.getPath();
        if (!id.getNamespace().equals("tfc") || soil(state)==null && !path.startsWith("mud_bricks/")) return state;
        String suffix=path.endsWith("_slab")?"_slab":path.endsWith("_stairs")?"_stairs":"";
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryBuild("tfc",
                path.substring(0,path.indexOf('/')+1)+soil+suffix)).map(b->b.withPropertiesOf(state)).orElse(state);
    }

    void fuelSignal(WorldGenRegion world,BlockPos pos) {
        var signal=world.getBlockState(pos);
        if (this==VANILLA || !signal.is(blocks.get(Blocks.CAMPFIRE))) return;
        // TFC consumes this log through its normal fuel data on the first ticking load.
        var data=entityTag(pos,signal,world);
        var oak=BuiltInRegistries.BLOCK.get(ResourceLocation.tryBuild("tfc","wood/log/oak"));
        var inventory=data.getCompound("inventory");
        var items=inventory.getList("Items",net.minecraft.nbt.Tag.TAG_COMPOUND);
        items.removeIf(entry -> ((CompoundTag)entry).getInt("Slot")==0);
        var log=RoadVersion.saveStack(new net.minecraft.world.item.ItemStack(oak),world);
        log.putInt("Slot",0);
        items.add(log);
        inventory.put("Items",items);
        data.put("inventory",inventory);
        world.getChunk(pos).setBlockEntityNbt(data);
    }

    private void bind(Block template,String path) {
        blocks.put(template,BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryBuild("tfc",RoadVersion.tfcMaterial(path))).orElseThrow());
    }
    BlockState state(BlockState template) {
        if (this==VANILLA) return template;
        var block=blocks.get(template.getBlock());
        if (block==null) throw new IllegalArgumentException("Missing road material for "+template);
        return block.withPropertiesOf(template);
    }

    static CompoundTag entityTag(BlockPos pos, BlockState state, WorldGenRegion level) {
        return RoadVersion.saveEntity(((EntityBlock)state.getBlock()).newBlockEntity(pos,state),level);
    }

}
