package com.frozendawn.debug.architect;

import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Chunk-bounded preparation of real Overworld terrain, in the disposable lab dimension only. */
final class ArchitectWildernessTerrain {
    static final int SIZE = 128;
    static final int RECIPE = 4;
    final ServerLevel level;
    final long seed;
    private final boolean construction;
    ArchitectConstructionCourse course;
    final List<Vec3> surface = new ArrayList<>();
    final List<Vec3> cave = new ArrayList<>();
    final List<BlockPos> bridgeDeck = new ArrayList<>();
    final Set<Long> acquiredChunks = new HashSet<>();
    private final List<Edit> edits = new ArrayList<>();
    private final Map<Long, Integer> trail = new HashMap<>();
    private final MessageDigest digest;
    private final List<String> nativeHashes = new ArrayList<>();
    private int chunk, column, editIndex, hashColumn;
    private boolean designed;
    private String hash;
    BlockPos gate, otherGate, shelterDoor, shelterExit, drift, growth;
    int shelterY;
    private record Edit(BlockPos pos, BlockState state) { }

    ArchitectWildernessTerrain(ServerLevel level, long seed) {
        this(level, seed, false);
    }

    ArchitectWildernessTerrain(ServerLevel level, long seed, boolean construction) {
        this.construction = construction;
        this.level = level;
        this.seed = seed;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    String progress() {
        if (chunk < 64) return "Native terrain snapshots " + chunk + "/64 chunks";
        if (column < SIZE * SIZE) return "Freezing terrain " + column / 128 + "/128 strips";
        if (!designed || editIndex < edits.size()) return "Building trails, caves, shelter and obstacles";
        return "Fingerprinting terrain " + hashColumn / 128 + "/128 strips";
    }

    boolean prepareTick() {
        if (chunk < 64) {
            int cx = chunk % 8, cz = chunk / 8;
            long packed = ChunkPos.asLong(cx, cz);
            if (!level.getForcedChunks().contains(packed)) {
                level.setChunkForced(cx, cz, true);
                acquiredChunks.add(packed);
            }
            level.getChunk(cx, cz);
            var manager = level.getStructureManager();
            var id = ResourceLocation.fromNamespaceAndPath("frozendawn", "lab/wilderness_native_v1/" + cx + "_" + cz);
            var saved = manager.get(id);
            var template = saved.orElseGet(() -> manager.getOrCreate(id));
            BlockPos origin = new BlockPos(cx * 16, level.getMinBuildHeight(), cz * 16);
            if (saved.isEmpty()) {
                template.fillFromWorld(level, origin, new Vec3i(16, level.getHeight(), 16), false, null);
                if (!manager.save(id)) throw new IllegalStateException("Could not save native wilderness snapshot " + id);
            } else if (!template.placeInWorld(level, origin, origin,
                    new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(0), 2)) {
                throw new IllegalStateException("Could not restore wilderness snapshot " + id);
            }
            nativeHashes.add(ArchitectDebugReports.sha256(template.save(new CompoundTag()).toString().getBytes(StandardCharsets.UTF_8)));
            // Do not retain millions of StructureBlockInfo objects in the structure cache.
            manager.remove(id);
            chunk++;
            return false;
        }
        if (column < SIZE * SIZE) {
            int end = Math.min(SIZE * SIZE, column + 128);
            for (; column < end; column++) freezeColumn(column % SIZE, column / SIZE);
            return false;
        }
        if (!designed) { design(); designed = true; }
        if (editIndex < edits.size()) {
            applyEdits(2048);
            return false;
        }
        if (hashColumn < SIZE * SIZE) {
            ByteBuffer states = ByteBuffer.allocate(level.getHeight() * Integer.BYTES);
            int end = Math.min(SIZE * SIZE, hashColumn + 128);
            for (; hashColumn < end; hashColumn++) {
                states.clear();
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
                    states.putInt(Block.getId(level.getBlockState(new BlockPos(hashColumn % SIZE, y, hashColumn / SIZE))));
                digest.update(states.array());
            }
            return false;
        }
        if (hash == null) hash = HexFormat.of().formatHex(digest.digest());
        return true;
    }

    private void freezeColumn(int x, int z) {
        RandomSource random = RandomSource.create(seed ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L));
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = level.getMinBuildHeight() + 1; y <= top; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(pos), replacement = null;
            if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.SHORT_GRASS)
                    || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)) replacement = Blocks.AIR.defaultBlockState();
            else if (s.is(Blocks.WATER)) replacement = Blocks.ICE.defaultBlockState();
            else if (s.is(Blocks.LAVA)) replacement = Blocks.OBSIDIAN.defaultBlockState();
            else if (s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)) replacement = ModBlocks.FROZEN_DIRT.get().defaultBlockState();
            else if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) replacement = ModBlocks.FROZEN_SAND.get().defaultBlockState();
            if (replacement != null) level.setBlock(pos, replacement, 2);
        }
        int ground = groundY(x, z);
        if (ground + 5 >= level.getMaxBuildHeight()) return;
        // Coherent drifts, capped at the same three full blocks as SnowAccumulator.
        double bank = Math.sin((x + seed % 31) * 0.14) + Math.cos((z - seed % 23) * 0.12);
        int depth = bank > 1.1 ? 3 : bank > 0.35 ? 2 : bank > -0.5 ? 1 : 0;
        for (int y = 1; y <= depth; y++) level.setBlock(new BlockPos(x, ground + y, z), Blocks.SNOW_BLOCK.defaultBlockState(), 2);
        BlockPos cap = new BlockPos(x, ground + depth + 1, z);
        float choice = random.nextFloat();
        if (choice < 0.13F) {
            int age = random.nextInt(4);
            level.setBlock(cap, ModBlocks.ACHERONITE_CRYSTAL.get().defaultBlockState()
                    .setValue(AcheroniteCrystalBlock.AGE, age)
                    .setValue(AcheroniteCrystalBlock.BURIED, age < 3 && depth > 0), 2);
        } else if (choice < 0.24F && depth == 0) {
            level.setBlock(cap, ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState(), 2);
        } else {
            level.setBlock(cap, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(7)), 2);
        }
    }

    private int groundY(int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        while (y > level.getMinBuildHeight() + 2 && !level.getBlockState(new BlockPos(x, y, z)).isFaceSturdy(level, new BlockPos(x, y, z), Direction.UP)) y--;
        return y;
    }

    static List<BlockPos> surfaceLoopColumns() {
        // Close at the actual corner. A spur to x18 would overlap the opening
        // segment in reverse and assign two independent heights to the same cells.
        int[][] corners = {{22,22},{50,22},{96,22},{108,22},{108,54},{108,90},
                {96,104},{54,104},{22,104},{22,86},{22,54},{22,22}};
        List<BlockPos> line = new ArrayList<>();
        for (int i = 1; i < corners.length; i++) {
            int ax = corners[i-1][0], az = corners[i-1][1], bx = corners[i][0], bz = corners[i][1];
            int steps = Math.max(Math.abs(bx-ax), Math.abs(bz-az));
            for (int n = 0; n < steps; n++) {
                int x = Math.round(ax + (bx-ax) * n / (float)steps), z = Math.round(az + (bz-az) * n / (float)steps);
                line.add(new BlockPos(x, 0, z));
            }
        }
        return List.copyOf(line);
    }

    private void design() {
        // Sparse graded trails connect the test stations; most terrain stays native.
        List<BlockPos> line = surfaceLoopColumns().stream()
                .map(p -> new BlockPos(p.getX(), groundY(p.getX(),p.getZ()), p.getZ())).toList();
        // Limit each trail step to one block. Repeat both directions to close the loop smoothly.
        int[] heights = line.stream().mapToInt(BlockPos::getY).toArray();
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 1; i < heights.length; i++) heights[i] = Math.clamp(heights[i], heights[i-1]-1, heights[i-1]+1);
            for (int i = heights.length-1; i >= 0; i--) {
                int next = heights[(i+1)%heights.length];
                heights[i] = Math.clamp(heights[i], next-1, next+1);
            }
        }
        shelterY = heights[line.indexOf(line.stream().filter(p -> p.getX()==108 && p.getZ()==54).findFirst().orElseThrow())];
        int fenceY = heights[line.indexOf(line.stream().filter(p -> p.getX()==66 && p.getZ()==22).findFirst().orElseThrow())];
        for (int i = 0; i < line.size(); i++) {
            BlockPos p = line.get(i);
            if (p.getX() == 108 && p.getZ() >= 44 && p.getZ() <= 64) heights[i] = shelterY;
            if (p.getZ() == 22 && p.getX() >= 62 && p.getX() <= 70) heights[i] = fenceY;
        }
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 1; i < heights.length; i++) if (!stationFloor(line.get(i))) heights[i]=Math.clamp(heights[i],heights[i-1]-1,heights[i-1]+1);
            for (int i = heights.length-2; i >= 0; i--) if (!stationFloor(line.get(i))) heights[i]=Math.clamp(heights[i],heights[i+1]-1,heights[i+1]+1);
        }
        for (int i = 0; i < line.size(); i++) {
            BlockPos p = new BlockPos(line.get(i).getX(), heights[i], line.get(i).getZ());
            trail.put(key(p.getX(),p.getZ()),p.getY());
            for (int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                BlockPos floor=p.offset(dx,0,dz);
                for(int dy=-4;dy<=0;dy++) put(floor.offset(0,dy,0), dy==0 ? ModBlocks.FROZEN_DIRT.get().defaultBlockState() : Blocks.STONE.defaultBlockState());
                for(int dy=1;dy<=4;dy++) put(floor.above(dy),Blocks.AIR.defaultBlockState());
            }
            if (p.getX() == 66 && p.getZ() == 22) {
                // Keep the gate in the route, but do not make its non-air block the path target.
                // Flank destinations require the villager to cross the open gate and leave a
                // clear endpoint on each side for route validation and the later closure trigger.
                BlockPos before = line.get(i - 2);
                BlockPos after = line.get(i + 2);
                surface.add(Vec3.atBottomCenterOf(new BlockPos(before.getX(), heights[i - 2] + 1, before.getZ())));
                surface.add(Vec3.atBottomCenterOf(new BlockPos(after.getX(), heights[i + 2] + 1, after.getZ())));
            } else if (i == line.size()-1 || i % 8 == 0 || (p.getX() == 108 && (p.getZ() == 46 || p.getZ() == 54 || p.getZ() == 62))) {
                surface.add(Vec3.atBottomCenterOf(p.above()));
            }
        }
        // Reassert centre surfaces after overlapping trail shoulders; keep true 1-block steps.
        for(BlockPos p:line) {
            BlockPos floor=new BlockPos(p.getX(),trail.get(key(p.getX(),p.getZ())),p.getZ());
            put(floor,ModBlocks.FROZEN_DIRT.get().defaultBlockState());
            for(int dy=1;dy<=3;dy++)put(floor.above(dy),Blocks.AIR.defaultBlockState());
        }
        for(int i=5;i<line.size();i+=17){
            if(i%8==0)continue; // Keep exact waypoint standing positions clear.
            BlockPos p=line.get(i);
            // Decorations on a one-block step can exceed the villager's physical jump height
            // even when vanilla marks the node reachable. Keep the graded lane clear.
            BlockPos next=line.get((i+1)%line.size());
            BlockPos shoulder=decorationColumn(p,next);
            int x=shoulder.getX(),z=shoulder.getZ();
            if(trail.containsKey(key(x,z)))continue;
            BlockPos cap=new BlockPos(x,groundY(x,z)+1,z);
            if((p.getX()==108&&p.getZ()>=38&&p.getZ()<=70)||(p.getZ()==104&&p.getX()>65&&p.getX()<80))continue;
            put(cap,i%2==0?ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState():Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,1+(i%7)));
        }
        buildCave();
        buildShelter();
        buildFence();
        buildRavine();
        // Fixed mutation stations are just ahead of the guided target's route.
        growth = floor(50,22).above();
        drift = floor(80,22).above();
        if (construction) course = new ArchitectConstructionCourse(this);
    }

    static BlockPos decorationColumn(BlockPos centre, BlockPos next) {
        int dx=Integer.signum(next.getX()-centre.getX()),dz=Integer.signum(next.getZ()-centre.getZ());
        return centre.offset(-dz*2,0,dx*2);
    }

    private static boolean stationFloor(BlockPos p) {
        return (p.getX()==108 && p.getZ()>=44 && p.getZ()<=64)
                || (p.getZ()==22 && p.getX()>=62 && p.getX()<=70);
    }

    private void buildCave() {
        int startY=floor(22,86).getY(), endY=floor(22,54).getY();
        int bottom=Math.max(level.getMinBuildHeight()+6,Math.min(startY,endY)-9);
        cave.add(Vec3.atBottomCenterOf(new BlockPos(22,startY+1,86)));
        cave.add(Vec3.atBottomCenterOf(new BlockPos(26,startY+1,86)));
        for (int z : new int[]{86,54}) for(int x=22;x<=30;x++) for(int dz=-1;dz<=1;dz++) {
            int y=z==86?startY:endY;
            put(new BlockPos(x,y,z+dz),Blocks.STONE.defaultBlockState());
            for(int dy=1;dy<=4;dy++)put(new BlockPos(x,y+dy,z+dz),Blocks.AIR.defaultBlockState());
        }
        for(int z=86;z>=54;z--) {
            int y=Math.max(bottom,Math.max(startY-(86-z),endY-(z-54)));
            for(int dx=-2;dx<=2;dx++) {
                put(new BlockPos(30+dx,y,z),Blocks.STONE.defaultBlockState());
                for(int dy=1;dy<=3;dy++)put(new BlockPos(30+dx,y+dy,z),Math.abs(dx)==2 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                if(z<84 && z>56)put(new BlockPos(30+dx,y+4,z),Blocks.STONE.defaultBlockState());
            }
            if(z%4==2)cave.add(Vec3.atBottomCenterOf(new BlockPos(30,y+1,z)));
            if(z%7==0 && z<82 && z>58)put(new BlockPos(31,y+1,z),ModBlocks.ACHERONITE_CRYSTAL.get().defaultBlockState().setValue(AcheroniteCrystalBlock.AGE,2));
        }
        cave.add(Vec3.atBottomCenterOf(new BlockPos(26,endY+1,54)));
        cave.add(Vec3.atBottomCenterOf(new BlockPos(22,endY+1,54)));
    }

    private void buildShelter() {
        for(int x=103;x<=113;x++)for(int z=49;z<=59;z++) {
            put(new BlockPos(x,shelterY,z),ModBlocks.FROZEN_STONE_BRICKS.get().defaultBlockState());
            for(int y=1;y<=4;y++) put(new BlockPos(x,shelterY+y,z),
                    y==4 || x==103 || x==113 || z==49 || z==59 ? ModBlocks.FROZEN_PLANKS.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        shelterDoor=new BlockPos(108,shelterY+1,49);
        shelterExit=new BlockPos(108,shelterY+1,59);
        for(BlockPos p:List.of(shelterDoor,shelterExit)) for(int y=0;y<3;y++)put(p.above(y),Blocks.AIR.defaultBlockState());
        for(int x=103;x<=113;x++) if(x!=108)put(new BlockPos(x,shelterY+5,49),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,5));
    }

    private void buildFence() {
        gate=floor(66,22).above();
        otherGate=new BlockPos(66,gate.getY(),27);
        for(int z=16;z<=30;z++) {
            BlockPos p=new BlockPos(66,gate.getY(),z);
            put(p.below(),ModBlocks.FROZEN_DIRT.get().defaultBlockState());
            if(z==22 || z==27)put(p,Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,Direction.EAST).setValue(FenceGateBlock.OPEN,true));
            else put(p,Blocks.OAK_FENCE.defaultBlockState());
        }
        // The open gate has a genuine walkable detour with the same elevation.
        for(int x=63;x<=69;x++)for(int z=21;z<=28;z++) {
            if(x==66)continue;
            BlockPos p=new BlockPos(x,gate.getY()-1,z);
            put(p,ModBlocks.FROZEN_DIRT.get().defaultBlockState());
            for(int y=1;y<=3;y++)put(p.above(y),Blocks.AIR.defaultBlockState());
        }
    }

    private void buildRavine() {
        // A narrow crossing cuts through the native southern hillside.
        for(int x=70;x<=74;x++)for(int z=96;z<=112;z++) {
            int y=floor(x,104).getY();
            for(int dy=-5;dy<=2;dy++)put(new BlockPos(x,y+dy,z),Blocks.AIR.defaultBlockState());
            put(new BlockPos(x,y-6,z),Blocks.STONE.defaultBlockState());
            if(z>=103 && z<=105) {
                BlockPos p=new BlockPos(x,y,z);put(p,Blocks.OAK_PLANKS.defaultBlockState());
                if(x>=71)bridgeDeck.add(p);
            }
        }
    }

    boolean applyEdits(int budget) {
        int end = Math.min(edits.size(), editIndex + budget);
        for (; editIndex < end; editIndex++) {
            Edit edit = edits.get(editIndex);
            level.setBlock(edit.pos, edit.state, 3);
        }
        return editIndex == edits.size();
    }

    void put(BlockPos pos,BlockState state) { if(!level.isOutsideBuildHeight(pos))edits.add(new Edit(pos,state)); }
    private static long key(int x,int z){return ((long)x<<32) ^ (z&0xffffffffL);}
    BlockPos floor(int x,int z){return new BlockPos(x,trail.getOrDefault(key(x,z),groundY(x,z)),z);}
    boolean contains(Vec3 p){return p.x>=0 && p.x<SIZE && p.z>=0 && p.z<SIZE && p.y>level.getMinBuildHeight();}
    String hash(){return hash;}
    List<String> nativeHashes(){return List.copyOf(nativeHashes);}
    void release(){for(long p:acquiredChunks)level.setChunkForced(ChunkPos.getX(p),ChunkPos.getZ(p),false);acquiredChunks.clear();}
}
