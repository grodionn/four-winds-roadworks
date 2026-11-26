package dev.fourwinds.roads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import static dev.fourwinds.roads.RoadLayout.*;

final class VillageLinks {
    static final TagKey<Structure> DESTINATIONS = TagKey.create(Registries.STRUCTURE, FourWinds.id("destinations"));
    static final ResourceLocation PIXIES = ResourceLocation.fromNamespaceAndPath("iceandfire", "pixie_village");
    private static final Map<StructureStart, Spur> LINKS = Collections.synchronizedMap(new WeakHashMap<>());

    static List<Spur> nearby(WorldGenRegion level,RoadGrade grade) {
        var result = new ArrayList<Spur>();
        var center = level.getCenter();
        var middle = new Point(center.getMiddleBlockX(), center.getMiddleBlockZ());
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var targets = registry.getTag(DESTINATIONS);
        if (targets.isEmpty()) return result;
        var state = level.getLevel().getChunkSource().getGeneratorState();
        var candidates = candidates(center, START_RADIUS, state, targets.get(), level.getSeed());
        for (ChunkPos pos : candidates) {
            // FEATURES guarantees structure starts in this radius. Never load a chunk from the live server.
            var chunk = level.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS);
            for (var target : targets.get()) {
                var start = chunk.getStartForStructure(target.value());
                if (start == null || !start.isValid()) continue;
                boolean pixies = target.unwrapKey().orElseThrow().location().equals(PIXIES);
                Spur spur = LINKS.computeIfAbsent(start, s -> route(level.getSeed(), s, pixies,grade));
                if (spur.near(middle,16)) result.add(spur);
            }
        }
        result.sort(java.util.Comparator.comparingInt(Spur::turn).thenComparingInt(Spur::across)
                .thenComparingInt(Spur::end).thenComparing(Spur::eastWest));
        return result;
    }

    private static java.util.Set<ChunkPos> candidates(ChunkPos center, int radius, ChunkGeneratorStructureState state,
            net.minecraft.core.HolderSet<Structure> targets, long seed) {
        var candidates = new HashSet<ChunkPos>();
        var placements = new HashSet<net.minecraft.world.level.levelgen.structure.placement.StructurePlacement>();
        for (var target : targets) placements.addAll(state.getPlacementsForStructure(target));
        for (var placement : placements) {
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                int step = spread.spacing();
                int minX = Math.floorDiv(center.x - radius, step) * step;
                int minZ = Math.floorDiv(center.z - radius, step) * step;
                for (int x = minX; x <= center.x + radius; x += step) {
                    for (int z = minZ; z <= center.z + radius; z += step) {
                        ChunkPos pos = spread.getPotentialStructureChunk(seed, x, z);
                        if (center.getChessboardDistance(pos) <= radius) candidates.add(pos);
                    }
                }
            } else {
                ChunkPos.rangeClosed(center,radius).filter(p->placement.isStructureChunk(state,p.x,p.z)).forEach(candidates::add);
            }
        }
        return candidates;
    }

    static List<RoadSection.Parcel> parcels(WorldGenRegion region,int x,int z,int rx,int rz,
            com.google.common.cache.Cache<Long,List<RoadSection.Parcel>> cache) {
        var level=region.getLevel();
        if (!level.structureManager().shouldGenerateStructures()) return List.of();
        var registry=region.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var targets=registry.getTag(DESTINATIONS);
        if (targets.isEmpty()) return List.of();
        var state=level.getChunkSource().getGeneratorState();
        var center=new ChunkPos(x>>4,z>>4);
        var result=new ArrayList<RoadSection.Parcel>();
        for (var pos:candidates(center,START_RADIUS+(Math.max(rx,rz)+15)/16,state,targets.get(),region.getSeed())) {
            long key=pos.toLong();
            var parcels=cache.getIfPresent(key);
            if (parcels==null) {
                net.minecraft.world.level.chunk.ChunkAccess start;
                if (pos.getChessboardDistance(region.getCenter())<=ShoreTerrain.SURVEY_RADIUS) {
                    start=region.getChunk(pos.x,pos.z,ChunkStatus.STRUCTURE_STARTS);
                } else {
                    // A section can be surveyed from its far bank. Recreate the seed's starts in memory
                    // without loading a live chunk or publishing a plan that depends on decoration order.
                    var scratch=new net.minecraft.world.level.chunk.ProtoChunk(pos,net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                            level,region.registryAccess().registryOrThrow(Registries.BIOME),null);
                    level.getChunkSource().getGenerator().createStructures(region.registryAccess(),state,level.structureManager(),scratch,level.getStructureManager());
                    start=scratch;
                }
                var pieces=new ArrayList<RoadSection.Parcel>();
                for (var target:targets.get()) {
                    var village=start.getStartForStructure(target.value());
                    if (village==null || !village.isValid()) continue;
                    if (target.unwrapKey().orElseThrow().location().equals(PIXIES)) {
                        // This single piece places streets and houses outside its original bounding box.
                        pieces.add(new RoadSection.Parcel(pos.getMinBlockX()-16,pos.getMinBlockZ()-16,pos.getMaxBlockX()+16,pos.getMaxBlockZ()+16,
                                level.getMinBuildHeight(),level.getMaxBuildHeight(),false));
                    } else if (compact(village)) {
                        var precinct=BoundingBox.encapsulatingBoxes(village.getPieces().stream().map(p->p.getBoundingBox()).toList()).orElseThrow();
                        pieces.add(new RoadSection.Parcel(precinct.minX(),precinct.minZ(),precinct.maxX(),precinct.maxZ(),
                                precinct.minY(),precinct.maxY(),true));
                        for (var piece:village.getPieces()) {
                            var b=piece.getBoundingBox();
                            if (b.getXSpan()==1 && b.getZSpan()==1) continue;
                            boolean street=piece instanceof PoolElementStructurePiece pool && pool.getElement().getProjection()==StructureTemplatePool.Projection.TERRAIN_MATCHING;
                            pieces.add(new RoadSection.Parcel(b.minX(),b.minZ(),b.maxX(),b.maxZ(),b.minY(),b.maxY(),street));
                        }
                    }
                }
                parcels=List.copyOf(pieces);cache.put(key,parcels);
            }
            for (var p:parcels) if (p.intersects(x-rx,z-rz,x+rx,z+rz)) result.add(p);
        }
        return result;
    }

    private static boolean compact(StructureStart settlement) {
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for (var piece:settlement.getPieces()) {
            var b=piece.getBoundingBox();
            if (b.getXSpan()<4 || b.getZSpan()<4 || piece instanceof PoolElementStructurePiece pool
                    && pool.getElement().getProjection()==StructureTemplatePool.Projection.TERRAIN_MATCHING) continue;
            low=Math.min(low,b.minY());high=Math.max(high,b.minY());
        }
        // A mountain village may sit on several terraces with a road safely below its houses.
        return high>=low && high-low<=16;
    }

    private static Spur route(long seed, StructureStart start, boolean pixies,RoadGrade grade) {
        var first = start.getPieces().getFirst().getBoundingBox().getCenter();
        // Ice and Fire expands its single piece's box after placement. Its original street always starts at (7,7).
        var village = pixies ? new Point(start.getChunkPos().getBlockX(7), start.getChunkPos().getBlockZ(7))
                : new Point(first.getX(), first.getZ());
        var origin = new Point(start.getChunkPos().getMiddleBlockX(), start.getChunkPos().getMiddleBlockZ());
        var doors = new ArrayList<Point>();
        var buildings = new ArrayList<BoundingBox>();
        for (var piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (piece instanceof PoolElementStructurePiece pool
                    && pool.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING) {
                int x = box.getCenter().getX(), z = box.getCenter().getZ();
                if (box.getXSpan() >= box.getZSpan()) {
                    doors.add(new Point(box.minX(), z));
                    doors.add(new Point(box.maxX(), z));
                } else {
                    doors.add(new Point(x, box.minZ()));
                    doors.add(new Point(x, box.maxZ()));
                }
            } else if (!pixies) buildings.add(box);
        }
        // A custom village can have one procedural piece, and no jigsaw streets at all.
        if (doors.isEmpty()) doors.add(village);
        Spur best = null;
        int bestCost = Integer.MAX_VALUE;
        boolean bypass=pixies || compact(start);
        for (Point door : doors) {
            for (boolean eastWest : new boolean[]{true, false}) {
                Spur candidate = toRoad(seed, door, eastWest, village, pixies);
                if (bypass) candidate=grade.connect(candidate);
                if (door.distanceTo(origin) > 110 || candidate.point(candidate.turn(), 0).distanceTo(origin) > 115) continue;
                int length = Math.abs(candidate.turn() - candidate.end());
                int cost = length + Math.max(0, 14 - length) * 6;
                Point sign = candidate.sign();
                if (!pixies) for (var piece : start.getPieces()) {
                    if (piece.getBoundingBox().intersects(sign.x(), sign.z(), sign.x(), sign.z())) { cost += 160; break; }
                }
                // Prefer entering an existing street from open terrain, without crossing a house.
                for (int d = 2; d < length; d += 3) {
                    Point p = candidate.point(candidate.end() + Integer.signum(candidate.turn() - candidate.end()) * d, 0);
                    for (BoundingBox box : buildings) if (box.intersects(p.x()-1, p.z()-1, p.x()+1, p.z()+1)) cost += 80;
                }
                if (cost < bestCost) { best = candidate; bestCost = cost; }
            }
        }
        return grade.connect(best!=null?best:toRoad(seed,origin,true,village,pixies));
    }
}
