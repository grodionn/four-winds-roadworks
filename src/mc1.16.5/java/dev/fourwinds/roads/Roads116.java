package dev.fourwinds.roads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.feature.structures.StructureTemplatePool;
import net.minecraft.world.level.material.Material;
import static dev.fourwinds.roads.RoadLayout.*;

public final class Roads116 implements LegacyRoads.Land {
    public interface Pending {
        CompoundTag fourwinds$getSurvey();
        void fourwinds$setSurvey(CompoundTag survey);
    }

    private static final class Survey {
        final LegacyRoads roads = new LegacyRoads();
        final Map<Long,Integer> heights = new LinkedHashMap<Long,Integer>(256, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long,Integer> eldest) { return size() > 65536; }
        };
    }
    private static final Map<ChunkGenerator,ThreadLocal<Survey>> SURVEYS = Collections.synchronizedMap(new WeakHashMap<>());
    private final WorldGenRegion region;
    private final ChunkGenerator generator;
    private final Survey chart;
    private final List<int[]> protectedPieces = new ArrayList<>();
    private final List<Point> destinations = new ArrayList<>();
    private final int minX, minZ;

    private Roads116(WorldGenRegion region, ChunkGenerator generator, CompoundTag survey) {
        this.region = region;
        this.generator = generator;
        synchronized (SURVEYS) {
            chart = SURVEYS.computeIfAbsent(generator, key -> ThreadLocal.withInitial(Survey::new)).get();
        }
        minX = region.getCenterX() * 16;
        minZ = region.getCenterZ() * 16;
        ListTag boxes = survey.getList("pieces", 11);
        for (int i = 0; i < boxes.size(); i++) protectedPieces.add(boxes.getIntArray(i));
        int[] villages = survey.getIntArray("villages");
        for (int i = 0; i + 1 < villages.length; i += 2) destinations.add(new Point(villages[i], villages[i+1]));
    }

    // The feature neighbourhood still contains distant settlement starts. Keep their coordinates
    // until lighting, when trees from neighbouring chunks have finished growing across the lane.
    public static void survey(WorldGenRegion region) {
        if (region.getLevel().dimension() != Level.OVERWORLD) return;
        int cx = region.getCenterX(), cz = region.getCenterZ();
        ChunkAccess owner = region.getChunk(cx, cz);
        if (!(owner instanceof ProtoChunk) || owner instanceof ImposterProtoChunk) return;
        ListTag boxes = new ListTag();
        List<Integer> villages = new ArrayList<>();
        for (int dz = -7; dz <= 7; dz++) for (int dx = -7; dx <= 7; dx++) {
            if (!region.hasChunk(cx + dx, cz + dz)) continue;
            ChunkAccess chunk = region.getChunk(cx + dx, cz + dz, ChunkStatus.STRUCTURE_STARTS, false);
            if (chunk == null) continue;
            for (StructureStart<?> start : chunk.getAllStarts().values()) {
                if (!start.isValid()) continue;
                if (start.getFeature() == StructureFeature.VILLAGE && !start.getPieces().isEmpty()) {
                    net.minecraft.core.Vec3i town = start.getPieces().get(0).getBoundingBox().getCenter();
                    villages.add(town.getX());
                    villages.add(town.getZ());
                }
                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox b = piece.getBoundingBox();
                    if (!b.intersects(cx * 16 - 36, cz * 16 - 36, cx * 16 + 51, cz * 16 + 51)) continue;
                    if (piece instanceof PoolElementStructurePiece && ((PoolElementStructurePiece)piece).getElement().getProjection()
                            == StructureTemplatePool.Projection.TERRAIN_MATCHING) continue;
                    boxes.add(new net.minecraft.nbt.IntArrayTag(new int[]{b.x0,b.y0,b.z0,b.x1,b.y1,b.z1}));
                }
            }
        }
        CompoundTag data = new CompoundTag();
        data.put("pieces", boxes);
        data.putIntArray("villages", villages.stream().mapToInt(Integer::intValue).toArray());
        ((Pending)owner).fourwinds$setSurvey(data);
    }

    public static void beforeLight(ServerLevel level, ChunkGenerator generator, List<ChunkAccess> chunks) {
        ChunkAccess owner = chunks.get(chunks.size() / 2);
        if (level.dimension() != Level.OVERWORLD || !(owner instanceof ProtoChunk) || owner instanceof ImposterProtoChunk
                || owner.getStatus().isOrAfter(ChunkStatus.LIGHT)) return;
        Pending pending = (Pending)owner;
        CompoundTag data = pending.fourwinds$getSurvey();
        if (data == null) return;
        WorldGenRegion region = new WorldGenRegion(level, chunks);
        Roads116 work = new Roads116(region, generator, data);
        work.chart.roads.generate(work, work.minX, work.minZ);
        pending.fourwinds$setSurvey(null);
        owner.setUnsaved(true);
    }

    private int surface(int x, int z) {
        long key = ((long)x << 32) | (z & 0xffffffffL);
        Integer cached = chart.heights.get(key);
        if (cached != null) return cached;
        int bed = generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG) - 1;
        int surface = generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG) - 1;
        int value = bed * 2 + (surface > bed ? 1 : 0);
        chart.heights.put(key,value);
        return value;
    }

    @Override public long seed() { return region.getSeed(); }
    @Override public int sea() { return region.getSeaLevel(); }
    @Override public int bed(int x,int z) { return surface(x,z) >> 1; }
    @Override public boolean wet(int x,int z) { return (surface(x,z) & 1) != 0; }
    @Override public boolean ocean(int x,int z) {
        return generator.getBiomeSource().getNoiseBiome(x >> 2, sea() >> 2, z >> 2).getBiomeCategory() == Biome.BiomeCategory.OCEAN;
    }
    @Override public List<Point> villages(int x, int z) { return destinations; }
    @Override public boolean occupied(int x, int y, int z) {
        for (int[] box : protectedPieces) {
            if (x>=box[0] && x<=box[3] && z>=box[2] && z<=box[5] && y+9>=box[1] && y-3<=box[4]) return true;
        }
        return false;
    }
    @Override public boolean firm(int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);
        BlockState state=region.getBlockState(pos);
        return natural(x,y,z) && !state.is(BlockTags.LOGS) && state.getMaterial()!=Material.LEAVES
                && state.isFaceSturdy(region,pos,Direction.UP);
    }
    @Override public boolean openForStop(int x,int y,int z) {
        BlockState state=region.getBlockState(new BlockPos(x,y,z));
        return state.isAir() || state.getMaterial().isReplaceable() && state.getFluidState().isEmpty() && state.getMaterial()!=Material.LEAVES;
    }
    @Override public boolean natural(int x, int y, int z) {
        if (x < minX || x >= minX+16 || z < minZ || z >= minZ+16 || y < 1 || y >= 255) return false;
        BlockState state = region.getBlockState(new BlockPos(x,y,z));
        Material material = state.getMaterial();
        return state.isAir() || material.isReplaceable() || material == Material.LEAVES || material == Material.WATER
                || state.is(BlockTags.LOGS) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
                || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.GRASS_PATH) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY) || state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.TERRACOTTA);
    }
    @Override public void put(int x, int y, int z, LegacyRoads.Material mat, int dx, int dz) {
        if (mat==LegacyRoads.Material.BANK_SURFACE) {
            region.setBlock(new BlockPos(x,y,z),region.getBlockState(new BlockPos(x,bed(x,z),z)),18);
            return;
        }
        Block block;
        switch (mat) {
            case AIR: block = Blocks.AIR; break;
            case STONE: block = Blocks.STONE_BRICKS; break;
            case DRESSED: block = Blocks.POLISHED_ANDESITE; break;
            case WORN: block = Blocks.CRACKED_STONE_BRICKS; break;
            case COBBLE: block = Blocks.COBBLESTONE; break;
            case SLIM_POST: block = Blocks.SPRUCE_FENCE; break;
            case EARTH: block = Blocks.COARSE_DIRT; break;
            case GRAVEL: block = Blocks.GRAVEL; break;
            case PATH: block = Blocks.GRASS_PATH; break;
            case FILL: block = Blocks.STONE; break;
            case PLANK: block = Blocks.SPRUCE_PLANKS; break;
            case POST: case BEAM: block = Blocks.STRIPPED_SPRUCE_LOG; break;
            case LOW_STONE: block = Blocks.STONE_BRICK_SLAB; break;
            case LOW_TIMBER: block = Blocks.SPRUCE_SLAB; break;
            case STAIR: block = Blocks.STONE_BRICK_STAIRS; break;
            case TIMBER_STAIR: block = Blocks.SPRUCE_STAIRS; break;
            default: throw new AssertionError(mat);
        }
        BlockState state = block.defaultBlockState();
        if (block instanceof StairBlock) state = state.setValue(StairBlock.FACING,
                dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH);
        if (mat == LegacyRoads.Material.BEAM) state = state.setValue(RotatedPillarBlock.AXIS, dx == 0 ? Direction.Axis.X : Direction.Axis.Z);
        region.setBlock(new BlockPos(x,y,z),state,18);
    }
    @Override public void lighthouse(PierLayout p, int tileX, int tileZ) {
        Rotation rotation = p.eastWest() ? (p.outward()>0 ? Rotation.NONE : Rotation.CLOCKWISE_180)
                : (p.outward()>0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90);
        Direction side = p.eastWest() ? (p.side()>0 ? Direction.SOUTH : Direction.NORTH)
                : (p.side()>0 ? Direction.EAST : Direction.WEST);
        Mirror mirror = rotation.rotate(Direction.SOUTH)==side ? Mirror.NONE : Mirror.LEFT_RIGHT;
        net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings settings =
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setMirror(mirror)
                        .setRotation(rotation).setKnownShape(true).setIgnoreEntities(true)
                        .setBoundingBox(new BoundingBox(tileX,1,tileZ,tileX+15,254,tileZ+15));
        Point start = p.point(0,PierLayout.MIN_SIDE);
        BlockPos origin = new BlockPos(start.x(),p.deck()+1,start.z());
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate drawing =
                region.getLevel().getStructureManager().get(FourWinds.id("coastal_lighthouse"));
        if (drawing != null) drawing.placeInWorld(region,origin,origin,settings,new java.util.Random(origin.asLong()),18);
    }
    @Override public void waypost(int x, int y, int z, Spur target) {
        BlockPos pos = new BlockPos(x,y+1,z);
        region.setBlock(pos.below(),Blocks.OAK_FENCE.defaultBlockState(),18);
        region.setBlock(pos,Blocks.OAK_SIGN.defaultBlockState(),18);
        int dx = target == null ? 0 : target.village().x()-x;
        int dz = target == null ? 0 : target.village().z()-z;
        Direction to = Math.abs(dx) >= Math.abs(dz) ? (dx>=0 ? Direction.EAST : Direction.WEST) : (dz>=0 ? Direction.SOUTH : Direction.NORTH);
        String prefix = "sign.fourwindsroadworks.";
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id","minecraft:sign");
        nbt.putInt("x",x); nbt.putInt("y",y+1); nbt.putInt("z",z);
        Component[] lines = {RoadGame.translated(prefix+(target == null ? "crossing" : "village")),
                RoadGame.translated(prefix+(target == null ? "north" : to.getName())),
                target == null ? RoadGame.translated(prefix+"west_east") : RoadGame.translated(prefix+"distance",Math.round(Math.hypot(dx,dz))),
                target == null ? RoadGame.translated(prefix+"south") : RoadGame.literal("")};
        for (int i=0; i<4; i++) nbt.putString("Text"+(i+1),Component.Serializer.toJson(lines[i]));
        region.getChunk(x >> 4,z >> 4).setBlockEntityNbt(nbt);
    }
}
