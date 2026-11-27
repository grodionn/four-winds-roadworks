package dev.fourwinds.roads;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.material.Fluid;
import static dev.fourwinds.roads.RoadLayout.*;

final class RoadTerrain {
    static final TagKey<Block> GROUND = TagKey.create(RoadApi.BLOCK, FourWinds.id("terrain"));
    static final TagKey<Block> GROWTH = TagKey.create(RoadApi.BLOCK, FourWinds.id("roadside_growth"));
    static final TagKey<Fluid> WATER = TagKey.create(RoadApi.FLUID, FourWinds.id("bridge_water"));
    static final TagKey<Block> ICE = TagKey.create(RoadApi.BLOCK, FourWinds.id("frozen_crossings"));
    static final TagKey<Biome> SEA = TagKey.create(RoadApi.BIOME, FourWinds.id("open_sea"));
    private static final TagKey<Biome> SHORE = TagKey.create(RoadApi.BIOME, FourWinds.id("tidal_shore"));
    private static final TagKey<Fluid> SALT = TagKey.create(RoadApi.FLUID,
            RoadGame.id("tfc","salt_water"));
    private static final RoadColumn VOID = new RoadColumn(-1000, Blocks.AIR.defaultBlockState(), false, false);
    static final int TUNNEL_HEIGHT=6;
    record RoadColumn(int y, BlockState state, boolean water, boolean sea) {
        int deck() { return y + (water ? 2 : 0); }
        boolean valid() { return this != VOID; }
        boolean rockCover(int deck) { return !water && y-deck>=10; }
        boolean needsTunnel(int grade) { return y >= grade+TUNNEL_HEIGHT+1 || water && y>grade; }
    }

    final WorldGenRegion level;
    final RoadGrade grade;
    final RoadMaterials materials;
    final RoadClearance clearance=new RoadClearance();
    final BridgeSurvey bridges;
    final TunnelWorks tunnels=new TunnelWorks(this);
    private final BiomeManager shoreBiomes;
    private final Long2ObjectOpenHashMap<RoadColumn> samples = new Long2ObjectOpenHashMap<>();
    private final java.util.Map<Long,Boolean> coast = new java.util.HashMap<>();
    private final java.util.Map<Long,Boolean> tidePools = new java.util.HashMap<>();
    private static final net.minecraft.resources.ResourceKey<Biome> TIDAL_FLATS = net.minecraft.resources.ResourceKey.create(
            RoadApi.BIOME,RoadGame.id("tfc","tidal_flats"));
    private record ProtectedPiece(BoundingBox box, boolean street, boolean pixies) {}
    private final java.util.Map<Long,List<ProtectedPiece>> structuresByChunk=new java.util.HashMap<>();

    RoadTerrain(WorldGenRegion level,RoadGrade grade) {
        bridges=new BridgeSurvey(this);
        this.level = level;
        this.grade=grade;
        materials = RoadMaterials.forChunk(level);
        var chunk = level.getCenter();
        var source=level.getLevel().getChunkSource();
        // Biome zoom can sample one quart beyond the FEATURES region on an outer shoreline column.
        shoreBiomes=level.getBiomeManager().withDifferentSource((x,y,z)-> {
            if (Math.abs((x>>2)-chunk.x)<=1 && Math.abs((z>>2)-chunk.z)<=1) return level.getNoiseBiome(x,y,z);
            return RoadGame.biome(source,x,y,z);
        });
    }

    private List<ProtectedPiece> protection(Point p) {
        var chunk=new ChunkPos(p.x()>>4,p.z()>>4);
        return structuresByChunk.computeIfAbsent(chunk.toLong(),key->{
            var structures=level.registryAccess().registryOrThrow(RoadApi.STRUCTURE);
            return VillageAccess.inChunk(level,chunk).stream()
                .flatMap(start -> {
                    var holder = structures.getHolderOrThrow(structures.getResourceKey(VillageAccess.type(start)).orElseThrow());
                    boolean village = holder.is(VillageAccess.DESTINATIONS);
                    boolean pixies = VillageLinks.PIXIES.equals(structures.getKey(VillageAccess.type(start)));
                    return start.getPieces().stream().map(piece -> new ProtectedPiece(piece.getBoundingBox(),
                            village && piece instanceof PoolElementStructurePiece pool
                                && pool.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING,pixies));
                })
                .filter(piece -> piece.box.intersects(chunk.getMinBlockX(), chunk.getMinBlockZ(),chunk.getMaxBlockX(),chunk.getMaxBlockZ()))
                .toList();
        });
    }

    RoadColumn column(Point p) {
        long key = ((long)p.x() << 32) | Integer.toUnsignedLong(p.z());
        RoadColumn known = samples.get(key);
        if (known != null) return known;
        var pos = new BlockPos.MutableBlockPos(p.x(), 0, p.z());
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.x(), p.z()) - 1;
        RoadColumn found = VOID;
        for (int y = top; y >= level.getMinBuildHeight(); y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            boolean ice = state.is(ICE);
            boolean water = state.getFluidState().is(WATER) || ice && waterUnderIce(pos);
            if (water || ice || naturalGround(state) || state.is(Blocks.DIRT_PATH)) {
                int tide = level.getSeaLevel()-1;
                // TFC tide pools can expose isolated boulders in otherwise submerged columns.
                boolean shoal = !water && y >= tide && y <= tide+1 && unstableTidalShelf(p,tide);
                found = new RoadColumn(y, state, water, shoal || water && marine(pos,state));
                break;
            }
            if (!canClear(state)) break;
        }
        samples.put(key, found);
        return found;
    }

    private boolean waterUnderIce(BlockPos surface) {
        var probe=surface.mutable();
        while (probe.getY()>level.getMinBuildHeight()) {
            var below=level.getBlockState(probe.move(Direction.DOWN));
            if (below.getFluidState().is(WATER)) return true;
            if (!below.is(ICE)) return false;
        }
        return false;
    }

    int seabed(Point p) {
        var world=level.getLevel();
        return RoadGame.baseHeight(world,p.x(),p.z(),Heightmap.Types.OCEAN_FLOOR_WG)-1;
    }

    boolean unstableTidalShelf(Point p,int water) {
        if (materials.usesLanterns()) return false;
        int cx=p.x()>>4, cz=p.z()>>4;
        // A TFC tide pool extends seven blocks from its origin, even across a biome or chunk edge.
        boolean tidal=tidePools.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(cx,cz),ignored->{
            var chunks=level.getLevel().getChunkSource();
            for (int x=cx*4-2;x<=cx*4+5;x++) for (int z=cz*4-2;z<=cz*4+5;z++) {
                if (RoadGame.biome(chunks,x,water>>2,z).is(TIDAL_FLATS)) return true;
            }
            return false;
        });
        return tidal && seabed(p)<water-1;
    }

    boolean ocean(BlockPos pos) { return shoreBiomes.getBiome(pos).is(SEA); }

    boolean marine(BlockPos pos, BlockState water) {
        var biome = shoreBiomes.getBiome(pos);
        if (biome.is(SEA)) return true;
        if (!biome.is(SHORE) && !water.getFluidState().is(SALT)) return false;
        int cx=pos.getX()>>4, cz=pos.getZ()>>4;
        return coast.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(cx,cz), key -> nearOpenWater(cx,cz));
    }

    private boolean nearOpenWater(int cx,int cz) {
        int x=cx*16+8, z=cz*16+8;
        for (Direction face : Direction.Plane.HORIZONTAL) {
            if (seaLevelBiome(x+face.getStepX()*40,z+face.getStepZ()*40).is(SEA)) return true;
        }
        return false;
    }

    Holder<Biome> seaLevelBiome(int x,int z) {
        var chunks = level.getLevel().getChunkSource();
        return RoadGame.biome(chunks,x>>2,level.getSeaLevel()>>2,z>>2);
    }

    static boolean naturalGround(BlockState state) {
        if (state.is(GROUND)) return true;
        var id=RoadApi.blockId(state.getBlock());
        // TFC's pannable river gravel lives outside the common ore tags.
        return id.getNamespace().equals("tfc") && id.getPath().startsWith("deposit/") && !state.hasBlockEntity();
    }

    static boolean canClear(BlockState state) {
        if (state.isAir() || state.is(GROWTH) || (!state.hasBlockEntity() && state.getFluidState().isEmpty()
                && (RoadApi.replaceable(state) || state.getBlock() instanceof net.minecraft.world.level.block.BushBlock))) return true;
        var id=RoadApi.blockId(state.getBlock());
        String path=id.getPath();
        if (id.getNamespace().equals("firmalife")) return path.startsWith("plant/");
        if (!id.getNamespace().equals("tfc") && !id.getNamespace().equals("afc")) return false;
        return path.startsWith("wood/twig/") || path.startsWith("wood/log/") || path.startsWith("wood/wood/")
                || path.startsWith("wood/leaves/") || path.startsWith("wood/fallen_leaves/") || path.startsWith("plant/")
                || path.startsWith("groundcover/") || path.startsWith("wild_crop/") || path.startsWith("ore/small_")
                || path.startsWith("rock/loose/") || path.startsWith("rock/mossy_loose/") || path.equals("tree_roots");
    }

    static boolean rockSurface(BlockState state) {
        var id=RoadApi.blockId(state.getBlock());
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || id.getNamespace().equals("tfc")
                && (id.getPath().startsWith("rock/raw/") || id.getPath().startsWith("rock/hardened/"));
    }

    static boolean softVerge(BlockState state) {
        var id=RoadApi.blockId(state.getBlock());
        return state.is(BlockTags.DIRT) || id.getNamespace().equals("tfc")
                && (id.getPath().startsWith("grass/") || id.getPath().startsWith("duff/")
                || id.getPath().startsWith("dirt/") || id.getPath().startsWith("coarse_dirt/"));
    }

    boolean protectedAt(Point p, int bottom, int top, boolean villageApproach) {
        return protectedAt(p,bottom,top,villageApproach,false);
    }

    boolean openStreetVerge(Point p,RoadColumn ground,int deck) {
        if (protection(p).isEmpty()) return false;
        if (ground.water()) return true;
        if (deck-ground.y()<3 || !ground.state().is(BlockTags.DIRT) && !ground.state().is(Blocks.GRAVEL)) return false;
        // Street template boxes include stream banks and standing trees. Recognise an actual crown
        // before treating any vertical logs as vegetation in the right of way.
        boolean logs=false,leaves=false;
        var pos=new BlockPos.MutableBlockPos(p.x(),ground.y(),p.z());
        for (int y=ground.y()+1;y<=Math.min(level.getMaxBuildHeight()-1,Math.max(deck+6,ground.y()+15));y++) {
            var block=level.getBlockState(pos.setY(y));
            if (!canClear(block) || block.hasBlockEntity()) return false;
            logs|=block.is(BlockTags.LOGS);
            leaves|=block.is(BlockTags.LEAVES);
        }
        return !logs || leaves;
    }

    boolean protectedAt(Point p,int bottom,int top,boolean villageApproach,boolean clearOfStreet) {
        for (ProtectedPiece piece : protection(p)) {
            if ((villageApproach || clearOfStreet) && piece.street || villageApproach && piece.pixies) continue;
            BoundingBox box = piece.box;
            if (box.maxY() >= bottom && box.minY() <= top && box.intersects(p.x(), p.z(), p.x(), p.z())) return true;
        }
        return false;
    }

    boolean crossesAboveStreet(Point p,int bottom,int deck) {
        boolean street=false;
        for (ProtectedPiece piece:protection(p)) {
            var box=piece.box;
            if (!box.intersects(p.x(),p.z(),p.x(),p.z()) || box.maxY()<bottom || box.minY()>deck+6) continue;
            if (!piece.street || deck<box.maxY()+5) return false;
            street=true;
        }
        return street;
    }

    boolean clear(Point p, int surface, int natural) {
        return clear(p,surface,natural,column(p).needsTunnel(surface)?TUNNEL_HEIGHT:0);
    }

    boolean clear(Point p,int surface,int natural,int headroom) {
        var pos = new BlockPos.MutableBlockPos(p.x(), surface, p.z());
        boolean tunnel=headroom>0;
        int top = tunnel ? surface+headroom : Math.max(natural, surface+6);
        while (!tunnel && top < level.getMaxBuildHeight()-1) {
            BlockState above=level.getBlockState(pos.setY(top+1));
            if (!above.is(BlockTags.LOGS)) break;
            top++;
        }
        for (int y = surface + 1; y <= top; y++) {
            BlockState s = level.getBlockState(pos.setY(y));
            if (!canClear(s) && !naturalGround(s) && !s.getFluidState().is(WATER) && !s.is(ICE)
                    && !(tunnel && s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))) return false;
        }
        // A spring can sit in a hollow immediately above a bore. Preserve the streambed when
        // the last solid course is excavated, before flowing water receives its first tick.
        var roof=new BlockPos(p.x(),top+1,p.z());
        boolean springBed=tunnel && naturalGround(level.getBlockState(roof.below())) && level.isEmptyBlock(roof)
                && springAtCrown(roof) && !protectedAt(p,top+1,top+1,false);
        for (int y = surface + 1; y <= top; y++) {
            if (!level.getBlockState(pos.setY(y)).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        if (springBed) put(p,top+1,Blocks.STONE_BRICKS);
        return true;
    }

    private boolean springAtCrown(BlockPos roof) {
        var probe=new BlockPos.MutableBlockPos();
        for (int dx=-4;dx<=4;dx++) for (int dz=-4;dz<=4;dz++) for (int dy=-1;dy<=3;dy++) {
            var fluid=level.getFluidState(probe.set(roof.getX()+dx,roof.getY()+dy,roof.getZ()+dz));
            if (fluid.is(WATER) && fluid.isSource()) return true;
        }
        return false;
    }

    void sealRoof(Point p,int y) {
        var pos=new BlockPos(p.x(),y,p.z());
        var state=level.getBlockState(pos);
        if (state.isAir()) {
            // A bore can open into a larger natural cavern. Only pack a joint that meets its roof.
            int join=1;
            while (join<=2 && level.getBlockState(pos.above(join)).isAir()) join++;
            if (join>2) return;
            lineOpening(p,y,y+join-1);
            return;
        }
        if (!state.hasBlockEntity() && !protectedAt(p,y,y,false)
                && (state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock
                || !state.getFluidState().isEmpty() || state.is(BlockTags.SAND)
                || RoadApi.blockId(state.getBlock()).getPath().startsWith("rock/gravel/")))
            put(p,y,Blocks.COBBLESTONE);
        else lineOpening(p,y,y);
    }

    void sealSide(Point p,int low,int high) {
        // Water needs a retaining face. A dry joint at a neighbouring arch stays open.
        for (int y=low;y<=high;y++)
            if (!level.getFluidState(new BlockPos(p.x(),y,p.z())).isEmpty()) lineOpening(p,y,y);
    }

    void lineOpening(Point p,int low,int high) {
        if (protectedAt(p,low,high,false)) return;
        var pos=new BlockPos.MutableBlockPos(p.x(),low,p.z());
        for (int y=low; y<=high; y++) {
            var state=level.getBlockState(pos.setY(y));
            if (!state.hasBlockEntity() && (state.isAir() || RoadApi.replaceable(state) || state.is(ICE)
                    || !state.getFluidState().isEmpty() && !state.isCollisionShapeFullBlock(level,pos))) {
                put(p,y,Blocks.STONE_BRICKS);
            }
        }
    }

    void faceBank(Point p,int low,int high) {
        if (protectedAt(p,low,high,false)) return;
        var pos=new BlockPos.MutableBlockPos(p.x(),low,p.z());
        for (int y=low;y<=high;y++) {
            var state=level.getBlockState(pos.setY(y));
            var id=RoadApi.blockId(state.getBlock());
            boolean soil=state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                    || id.getNamespace().equals("tfc") && (id.getPath().startsWith("dirt/")
                    || id.getPath().startsWith("rock/gravel/") || id.getPath().startsWith("mud/"));
            if (soil && !state.hasBlockEntity() && RoadApi.solid(level.getBlockState(pos.above())))
                put(p,y,Blocks.COBBLESTONE);
        }
    }

    void foundation(Point p,RoadColumn ground,int deck,Block stone,boolean countryTrack) {
        var pos=new BlockPos.MutableBlockPos(p.x(),ground.y(),p.z());
        int bottom=ground.y();
        if (countryTrack && bottom<deck && !ground.water() && deck-bottom<=4 && softVerge(ground.state())) {
            var earth=level.getBlockState(pos.setY(bottom-1));
            if (earth.is(GROUND) && !earth.hasBlockEntity() && earth.getFluidState().isEmpty()
                    && !(earth.getBlock() instanceof FallingBlock) && earth.isFaceSturdy(level,pos,Direction.UP)) {
                for (int y=bottom;y<deck;y++)
                    if (!level.getBlockState(pos.setY(y)).hasBlockEntity()) level.setBlock(pos,earth,2);
                return;
            }
        }
        if (ground.water()) {
            while (bottom>level.getMinBuildHeight() && bottom>ground.y()-18) {
                var state=level.getBlockState(pos.setY(bottom-1));
                if (state.hasBlockEntity() || !state.getFluidState().is(WATER)
                        || !(RoadApi.liquid(state) || RoadApi.replaceable(state)) || protectedAt(p,bottom-1,bottom-1,false)) break;
                bottom--;
            }
        }
        for (int y=bottom;y<deck;y++) if (!level.getBlockState(pos.setY(y)).hasBlockEntity()) put(p,y,stone);
    }

    void blendShoulder(Point p, RoadColumn ground, int target) {
        // Finish the toe at the waterline when there is no room for another course of soil.
        if (target>ground.y()+1) {
            for (Direction side:Direction.Plane.HORIZONTAL) {
                int adjacent=ShoreTerrain.surface(level,new Point(p.x()+side.getStepX(),p.z()+side.getStepZ()));
                if ((adjacent&1)!=0) target=Math.min(target,Math.max(ground.y(),(adjacent>>1)+1));
            }
            if (target==ground.y()) return;
        }
        var pos = new BlockPos.MutableBlockPos(p.x(), ground.y(), p.z());
        boolean openLip=false;
        if (target<ground.y()) {
            // A daylight cut can uncover a cave roof. Seat the turf on rock or expose the hollow.
            // Keeping the old cap here would isolate a pillar once the adjoining banks have been graded.
            int planned=target;
            while (target>=planned-2) {
                var bed=new BlockPos(p.x(),target-1,p.z());
                var state=level.getBlockState(bed);
                if (state.isFaceSturdy(level,bed,Direction.UP) && !caveSpike(state)) break;
                target--;
            }
            if (target<planned-2) { openLip=true;target=planned; }
        }
        BlockState cap = level.getBlockState(pos);
        if (cap != ground.state() || cap.hasBlockEntity()) return;
        BlockState subsoil = level.getBlockState(pos.setY(ground.y() - 1));
        if (!subsoil.is(GROUND) || subsoil.hasBlockEntity()) subsoil = materials.state(Blocks.COARSE_DIRT.defaultBlockState());
        BlockState cover = level.getBlockState(pos.setY(ground.y() + 1));
        if (openLip && cover.is(BlockTags.LOGS) || protectedAt(p,Math.min(target,ground.y())-(openLip?4:1),Math.max(target,ground.y())+1,false)) return;
        boolean extendTrunk = cover.is(BlockTags.LOGS) && target < ground.y() && target >= ground.y() - 4;
        int bottom = Math.min(ground.y(), target), top = Math.max(ground.y(), target);
        for (int y = bottom; y <= top + 1; y++) {
            var state = level.getBlockState(pos.setY(y));
            if (extendTrunk && y == ground.y() + 1) continue;
            if (state.hasBlockEntity() || state.is(BlockTags.LOGS) || !state.getFluidState().isEmpty()
                    || (!state.is(GROUND) && !canClear(state) && !(target<ground.y() && caveSpike(state)))) return;
        }
        if (!extendTrunk && level.getBlockState(pos.setY(ground.y() + 2)).is(BlockTags.LOGS)) return;
        if (!extendTrunk && !cover.isAir() && !cover.is(BlockTags.LEAVES)) level.setBlock(pos.setY(ground.y()+1),Blocks.AIR.defaultBlockState(),2);
        for (int y = bottom; y <= top; y++) {
            BlockState next = openLip ? Blocks.AIR.defaultBlockState() : y == target ? cap : y < target ? subsoil : extendTrunk ? cover : Blocks.AIR.defaultBlockState();
            level.setBlock(pos.setY(y),next,2);
        }
        if (openLip) {
            for (int y=target-1;y>=target-4;y--) {
                var spike=pos.setY(y);
                if (!caveSpike(level.getBlockState(spike))) break;
                level.setBlock(spike,Blocks.AIR.defaultBlockState(),2);
            }
            return;
        }
        var coverId = RoadApi.blockId(cover.getBlock());
        if ((coverId.getNamespace().equals("tfc") && coverId.getPath().startsWith("groundcover/")
                || cover.is(RoadVersion.SHORT_GRASS) || cover.is(Blocks.FERN) || cover.is(BlockTags.SMALL_FLOWERS))
                && level.isEmptyBlock(pos.setY(target+1)) && cover.canSurvive(level,pos)) {
            level.setBlock(pos,cover,2);
        }
    }

    private static boolean caveSpike(BlockState state) {
        var id=RoadApi.blockId(state.getBlock());
        return id.getNamespace().equals("tfc") && id.getPath().startsWith("rock/spike/");
    }

    void put(Point p, int y, Block block) { put(p, y, block.defaultBlockState()); }
    void put(Point p, int y, BlockState state) {
        put(p,y,state,materials);
    }

    void put(Point p,int y,BlockState state,RoadMaterials palette) {
        level.setBlock(new BlockPos(p.x(),y,p.z()),palette.state(state),2);
    }
}
