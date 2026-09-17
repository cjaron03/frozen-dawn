package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Spawn conditions and invariants for the shared, generated structure templates. */
public enum ArchitectLabScenario {
    CLEAR_CORRIDOR("clear_corridor", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 220, 0),
    LOW_CEILING("low_ceiling", new Vec3(4.5, 1.05, 4.7), new Vec3(4.5, 2, 12.5), 220, 1),
    UNREACHABLE_TARGET("unreachable_target", new Vec3(4.5, 1, 4.5), new Vec3(12.5, 1, 4.5), 300, 2),
    SEALED_POCKET("sealed_pocket", new Vec3(4.5, 1, 4.5), new Vec3(8.5, 1, 4.5), 850, 0),
    WALL("wall", new Vec3(4.5, 1, 4.5), new Vec3(16.5, 1, 4.5), 600, 6),
    DOGLEG("dogleg", new Vec3(4.5, 1, 4.5), new Vec3(12.5, 1, 12.5), 400, 0),
    U_DETOUR("u_detour", new Vec3(5.5, 1, 5.5), new Vec3(9.5, 1, 5.5), 600, 0),
    CHEAP_DETOUR("cheap_detour", new Vec3(6.5, 1, 7.5), new Vec3(14.5, 1, 7.5), 400, 0),
    STAIRS_UP("stairs_up", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 4, 12.5), 400, 0),
    STAIRS_DOWN("stairs_down", new Vec3(4.5, 4, 4.5), new Vec3(4.5, 1, 13.5), 400, 0),
    NARROW_BRIDGE("narrow_bridge", new Vec3(4.5, 4, 10.5), new Vec3(16.5, 4, 10.5), 400, 0),
    OFFSET_DOORWAYS("offset_doorways", new Vec3(4.5, 1, 4.5), new Vec3(8.5, 1, 14.5), 500, 0),
    SLAB_STEPS("slab_steps", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 3, 12.5), 400, 0),
    VANISHING_WALL("vanishing_wall", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 350, 0),
    TARGET_JUKE("target_juke", new Vec3(4.5, 1, 4.5), new Vec3(14.5, 1, 4.5), 450, 0),
    SEEDED_MAZE("seeded_maze", new Vec3(3.5, 1, 3.5), new Vec3(17.5, 1, 17.5), 1600, 0),
    CLOSING_PASSAGE("closing_passage", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 400, 0),
    LAVA_DETOUR("lava_detour", new Vec3(6.5, 1, 10.5), new Vec3(15.5, 1, 10.5), 400, 0),
    CORRIDOR_SOAK("corridor_soak", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 1050, 0),
    CORRIDOR_SHUTTLE("corridor_shuttle", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 750, 0),
    PIT_SHALLOW("pit_shallow", new Vec3(5.5, 2, 10.5), new Vec3(11.5, 1, 10.5), 600, 0),
    PIT_DIRECT_STEPS("pit_direct_steps", new Vec3(4.5, 4, 10.5), new Vec3(12.5, 1, 10.5), 600, 0),
    PIT_SIDE_STEPS("pit_side_steps", new Vec3(7.5, 5, 10.5), new Vec3(12.5, 1, 10.5), 600, 0),
    PIT_CORNER_STEPS("pit_corner_steps", new Vec3(5.5, 4, 5.5), new Vec3(12.5, 1, 12.5), 600, 0),
    PIT_NARROW_STEPS("pit_narrow_steps", new Vec3(4.5, 4, 10.5), new Vec3(12.5, 1, 10.5), 600, 0),
    PIT_SLAB_RAMP("pit_slab_ramp", new Vec3(4.5, 3, 10.5), new Vec3(12.5, 1, 10.5), 600, 0),
    PIT_TUNNEL("pit_tunnel", new Vec3(4.5, 1, 10.5), new Vec3(12.5, 1, 10.5), 600, 0),
    PIT_TARGET_OFFSET("pit_target_offset", new Vec3(4.5, 4, 10.5), new Vec3(14.65, 1, 10.5), 600, 0),
    FENCE_GAP("fence_gap", new Vec3(5.5, 1, 10.5), new Vec3(15.5, 1, 10.5), 600, 0),
    FENCE_DETOUR("fence_detour", new Vec3(5.5, 1, 10.5), new Vec3(15.5, 1, 10.5), 600, 0),
    FENCE_CORNER("fence_corner", new Vec3(10.5, 1, 10.5), new Vec3(10.5, 1, 5.5), 800, 0),
    GATE_OPEN("gate_open", new Vec3(5.5, 1, 10.5), new Vec3(15.5, 1, 10.5), 600, 0),
    GATE_CLOSED("gate_closed", new Vec3(5.5, 1, 10.5), new Vec3(15.5, 1, 10.5), 800, 1),
    GATE_REOPENS("gate_reopens", new Vec3(5.5, 1, 10.5), new Vec3(15.5, 1, 10.5), 600, 0),
    SLAB_CHECKERBOARD("slab_checkerboard", new Vec3(4.5, 1, 10.5), new Vec3(16.5, 1, 10.5), 600, 0),
    SLAB_TOP_TUNNEL("slab_top_tunnel", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 600, 0),
    SLAB_LOW_ROOF("slab_low_roof", new Vec3(4.5, 1.5, 4.5), new Vec3(4.5, 1.5, 12.5), 600, 0),
    SLAB_STAIR_MIX("slab_stair_mix", new Vec3(4.5, 1, 3.5), new Vec3(4.5, 1, 14.5), 600, 0),
    SLAB_FENCE_LANE("slab_fence_lane", new Vec3(4.5, 1.5, 4.5), new Vec3(4.5, 1.5, 12.5), 600, 0),
    SLAB_TRAPDOOR("slab_trapdoor", new Vec3(4.5, 1, 3.5), new Vec3(4.5, 1, 14.5), 600, 0),
    FOOTING_ICE("footing_ice", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 600, 0),
    FOOTING_HONEY("footing_honey", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 600, 0),
    FOOTING_SOUL_SAND("footing_soul_sand", new Vec3(4.5, 1, 4.5), new Vec3(4.5, 1, 12.5), 600, 0),
    FOOTING_SLAB_BRIDGE("footing_slab_bridge", new Vec3(4.5, 3.5, 10.5), new Vec3(16.5, 3.5, 10.5), 600, 0),
    MULTI_CHOICE("multi_choice", new Vec3(4.5, 1, 10.5), new Vec3(10.5, 1, 8.5), 600, 0),
    MULTI_CROSSING("multi_crossing", new Vec3(4.5, 1, 10.5), new Vec3(14.5, 1, 7.5), 800, 0),
    MULTI_TARGET_REMOVED("multi_target_removed", new Vec3(4.5, 1, 10.5), new Vec3(9.5, 1, 10.5), 800, 0),
    MULTI_NEAR_ENCLOSED("multi_near_enclosed", new Vec3(4.5, 1, 10.5), new Vec3(8.5, 1, 10.5), 1200, 0),
    FIELD_FENCES("field_fences", new Vec3(3.5, 1, 3.5), new Vec3(17.5, 1, 17.5), 1200, 0),
    FIELD_SLABS("field_slabs", new Vec3(3.5, 1, 3.5), new Vec3(17.5, 1, 17.5), 1200, 0),
    FIELD_MIXED("field_mixed", new Vec3(3.5, 1, 3.5), new Vec3(17.5, 1, 17.5), 1200, 0),
    SCAFFOLD_ASCENT("scaffold_ascent", new Vec3(5.5, 1, 10.5), new Vec3(15.5, 8, 10.5), 1800, 0),
    SCAFFOLD_GAP("scaffold_gap", new Vec3(4.5, 6, 10.5), new Vec3(16.5, 6, 10.5), 1800, 0),
    SCAFFOLD_INTERRUPTION("scaffold_interruption", new Vec3(5.5, 1, 10.5), new Vec3(15.5, 8, 10.5), 4200, 0),
    SCAFFOLD_DAMAGE("scaffold_damage", new Vec3(4.5, 6, 10.5), new Vec3(16.5, 6, 10.5), 4200, 0),
    DIG_DOWN_REQUIRED("dig_down_required", new Vec3(6.5, 7, 10.5), new Vec3(6.5, 1, 10.5), 1800, 12),
    DIG_DOWN_OPEN("dig_down_open", new Vec3(6.5, 7, 10.5), new Vec3(6.5, 1, 10.5), 1800, 0),
    DIG_UP_REQUIRED("dig_up_required", new Vec3(6.5, 1, 10.5), new Vec3(6.5, 7, 10.5), 1800, 12),
    DIG_UP_OPEN("dig_up_open", new Vec3(6.5, 1, 10.5), new Vec3(6.5, 7, 10.5), 1800, 0),
    MIXED_ESCAPE("mixed_escape", new Vec3(3.5, 6, 10.5), new Vec3(17.5, 9, 10.5), 4200, 6),
    ROUTE_OPENS_MINING("route_opens_mining", new Vec3(4.5, 1, 10.5), new Vec3(16.5, 1, 10.5), 4200, 0),
    ROUTE_CLOSES_TRAVEL("route_closes_travel", new Vec3(4.5, 1, 10.5), new Vec3(16.5, 1, 10.5), 4200, 0),
    TARGET_TURNOVER("target_turnover", new Vec3(4.5, 1, 4.5), new Vec3(16.5, 1, 4.5), 6600, 0),
    LONG_PURSUIT("long_pursuit", new Vec3(4.5, 1, 4.5), new Vec3(16.5, 1, 4.5), 12600, 0);

    public final String id;
    public final Vec3 actorStart;
    public final Vec3 targetStart;
    public final int timeout;
    public final int excavationBudget;
    ArchitectLabScenario(String id, Vec3 actorStart, Vec3 targetStart, int timeout, int excavationBudget) {
        this.id = id; this.actorStart = actorStart; this.targetStart = targetStart;
        this.timeout = timeout; this.excavationBudget = excavationBudget;
    }
    public boolean lifecycleCase() { return ordinal() >= SCAFFOLD_ASCENT.ordinal(); }
    public int minimumDuration() {
        if (!lifecycleCase()) return 0;
        return timeout == 1800 ? 600 : timeout - 600;
    }
    public boolean expandedCase() { return ordinal() >= FENCE_GAP.ordinal(); }
    public boolean fieldCase() { return id.startsWith("field_"); }
    public boolean multipleTargetsCase() { return id.startsWith("multi_"); }
    public boolean requiresMeleeHit() { return holeCase() || expandedCase(); }
    public boolean holeCase() { return id.startsWith("pit_"); }
    public boolean stressCase() {
        return switch (this) {
            case CLEAR_CORRIDOR, LOW_CEILING, UNREACHABLE_TARGET, SEALED_POCKET -> false;
            default -> true;
        };
    }
    public ResourceLocation template() { return ResourceLocation.fromNamespaceAndPath("frozendawn", "lab/" + id); }
    public static ArchitectLabScenario named(String name) { return valueOf(name.toUpperCase(Locale.ROOT)); }
    public String fingerprint() {
        try (var in = ArchitectLabScenario.class.getResourceAsStream("/data/frozendawn/structure/lab/" + id + ".nbt")) {
            if (in == null) throw new IllegalStateException("Missing fixture " + id);
            return ArchitectDebugReports.sha256(in.readAllBytes());
        } catch (IOException error) { throw new IllegalStateException("Cannot fingerprint " + id, error); }
    }
    public List<BlockPos> preservedBlocks() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 1; x <= 19; x++) {
            for (int z = 1; z <= 19; z++) positions.add(new BlockPos(x, 0, z));
        }
        if (this == LOW_CEILING) {
            for (int z = 5; z <= 13; z++) positions.add(new BlockPos(4, 1, z));
            positions.add(new BlockPos(4, 3, 3)); // The other ceiling is not needed to escape.
        }
        if (this == STAIRS_UP || this == STAIRS_DOWN || this == SLAB_STEPS) {
            for (int z = 3; z <= 14; z++) for (int y = 1; y <= 3; y++) positions.add(new BlockPos(4, y, z));
        }
        if (this == NARROW_BRIDGE) {
            for (int x = 4; x <= 16; x++) for (int y = 1; y <= 3; y++) positions.add(new BlockPos(x, y, 10));
        }
        return List.copyOf(positions);
    }
}
