package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldModelTest {
    static final String DIM = "minecraft:overworld", EAST = "RETREAT_BEARING_E";
    static final UUID PLAYER = new UUID(1, 1), OBSERVER = new UUID(2, 2);
    static ObservedEvidence event(int encounter, long time, BlockPos pos) {
        return new ObservedEvidence(OBSERVER, new UUID(0, encounter), DIM, pos, time, "WITNESSED_SKY_BOUNDARY_CROSSING", true);
    }
    static WorldModel model() {
        var world = new WorldModel();
        world.sample(OBSERVER, DIM, BlockPos.ZERO, true, 10);
        world.access(DIM, new BlockPos(3, 0, 0), BlockPos.ZERO, event(1, 20, BlockPos.ZERO));
        return world;
    }

    @Test void resolvesByBearingAndDistanceRatherThanIdentityOfAnExit() {
        var world = model();
        world.access(DIM, new BlockPos(4, 0, 1), new BlockPos(1, 0, 1), event(2, 30, BlockPos.ZERO));
        assertEquals(new BlockPos(4, 0, 1), world.resolve(DIM, EAST, new BlockPos(8, 0, 1), 30).outside());
        assertNull(world.resolve(DIM, "RETREAT_BEARING_W", BlockPos.ZERO, 30));
        assertNull(world.resolve("minecraft:the_nether", EAST, BlockPos.ZERO, 30));
        assertNull(world.resolve(DIM, EAST, new BlockPos(40, 0, 0), 30));
        assertEquals("RETREAT_BEARING_N", WorldModel.bearing(BlockPos.ZERO, new BlockPos(0, 0, -4)));
        assertNull(WorldModel.bearing(null, BlockPos.ZERO));
    }

    @Test void directDisproofReplacesThePointWithFreshExplainableBlockedState() {
        var world = model();
        var wall = new BlockPos(2, 0, 0);
        assertTrue(world.obstructed(DIM, new BlockPos(3, 0, 0), event(2, 40, wall)));
        var blocked = world.snapshot(40).getFirst();
        assertEquals("BLOCKED", blocked.state());
        assertEquals(.9, blocked.confidence(), 1e-9);
        assertEquals(0, blocked.previousConfidence());
        assertEquals(1, blocked.contradictions());
        assertEquals(2, blocked.provenance().size());
        assertNull(world.resolve(DIM, EAST, BlockPos.ZERO, 40));
        assertFalse(world.obstructed(DIM, blocked.position(), event(2, 41, wall)));
        var loaded = WorldModel.load(world.save());
        assertEquals(world.snapshot(40), loaded.snapshot(40));
        // Only a fresh crossing can restore OPEN after a blocked discovery.
        loaded.access(DIM, blocked.position(), BlockPos.ZERO, event(3, 50, blocked.position()));
        assertEquals("OPEN", loaded.snapshot(50).getFirst().state());
        assertEquals(.2, loaded.snapshot(50).getFirst().confidence(), 1e-9);
    }

    @Test void pointConfidenceDeduplicatesAcrossReloadAndDecaysAfterTwentyDays() {
        var world = WorldModel.load(model().save());
        world.access(DIM, new BlockPos(3, 0, 0), BlockPos.ZERO, event(1, 30, BlockPos.ZERO));
        assertEquals(1, world.snapshot(30).getFirst().evidence());
        assertEquals(.2, world.snapshot(30 + 20 * 24000L).getFirst().confidence(), 1e-9);
        assertEquals(.1, world.snapshot(30 + 40 * 24000L).getFirst().confidence(), 1e-9);
        assertNull(world.sample(OBSERVER, DIM, BlockPos.ZERO, true, 40), "Transient movement cannot survive reload");
    }

    @Test void allHistoriesHaveDeterministicCapsAndUnknownIsDerived() {
        var first = model(); var second = model();
        for (int i = 0; i < 150; i++) {
            var pos = new BlockPos(i * 16, 0, 0);
            for (var world : List.of(first, second)) {
                world.event("DANGER_ZONE", DIM, pos, event(i + 2, 100, pos));
                world.sample(new UUID(3, i), DIM, pos, true, 100);
            }
        }
        assertEquals(first.save(), second.save());
        assertEquals(64, first.snapshot(100).size());
        assertEquals(128, first.save().getList("observed", Tag.TAG_COMPOUND).size());
        assertTrue(first.save().getList("covered", Tag.TAG_COMPOUND).size() <= 16);
        assertFalse(first.save().toString().contains("UNKNOWN"));
        assertFalse(first.unknown().isEmpty());
        for (int i = 0; i < 12; i++) first.event("HEAT_SOURCE", DIM, BlockPos.ZERO, event(i + 300, 200 + i, BlockPos.ZERO));
        assertEquals(8, first.snapshot(220).stream().filter(p -> p.label().equals("HEAT_SOURCE")).findFirst().orElseThrow().provenance().size());
    }

    @Test void malformedOrUnexplainedPointsCannotBecomeCandidates() {
        CompoundTag tag = model().save();
        var point = tag.getList("points", Tag.TAG_COMPOUND).getCompound(0);
        point.remove("provenance");
        assertTrue(WorldModel.load(tag).snapshot(100).isEmpty());
        tag = model().save(); point = tag.getList("points", Tag.TAG_COMPOUND).getCompound(0);
        point.putString("state", "INVENTED");
        assertTrue(WorldModel.load(tag).snapshot(100).isEmpty());
    }

    @Test void versionTwoLoadsWithoutWorldDataAndErasureSerializesNoSpatialResidue() {
        var data = new MaeveSavedData(); data.synchronize(false, true);
        data.store().observeContact(PLAYER, OBSERVER, DIM, 10).sample(OBSERVER, DIM, BlockPos.ZERO, true, 10);
        data.store().world(PLAYER).access(DIM, new BlockPos(3, 0, 0), BlockPos.ZERO, event(1, 20, BlockPos.ZERO));
        var backup = data.save(new CompoundTag(), null);
        assertEquals(6, backup.getInt("dataVersion"));
        var loaded = MaeveSavedData.load(backup, null);
        assertEquals(1, loaded.store().world(PLAYER).snapshot(20).size());
        loaded.erase(); loaded.erase();
        assertFalse(loaded.save(new CompoundTag(), null).contains("beliefs"));
        loaded.synchronize(false, true);
        assertNull(loaded.store().world(PLAYER));
        var legacy = new CompoundTag(); legacy.putInt("dataVersion", 2); legacy.putBoolean("activated", true);
        assertEquals(0, MaeveSavedData.load(legacy, null).store().size());
    }

    @Test void discoveryDoesNotRefreshContactOrCreateANewEncounterAndPersistsCooldown() {
        var store = new BeliefStore();
        for (int i = 0; i < 5; i++) store.record(PLAYER, OBSERVER, DIM, BlockPos.ZERO, i * 610L, EAST, true, "WITNESSED_OUTWARD_CROSSING");
        store.contact(PLAYER, 3050);
        var policy = store.commitment(PLAYER);
        var target = new MaeveDirector.SpatialTarget(BlockPos.ZERO, new BlockPos(3, 0, 0));
        assertTrue(policy.choose(PLAYER, OBSERVER, List.of(new MaeveDirector.PositionCandidate(EAST, target.outside(), null, 3, target)), 3050));
        policy.arrived(3280);
        var directive = policy.selected();
        assertTrue(store.disprove(directive, OBSERVER, new BlockPos(2, 0, 0), 3655));
        policy.discover(new BlockPos(2, 0, 0), 3655);
        assertEquals(directive.encounter(), policy.encounter());
        assertEquals(.35, store.snapshot(PLAYER, 3655).getFirst().confidence(), 1e-9);
        assertNotNull(policy.active(3714)); assertNull(policy.active(3715));
        var loaded = BeliefStore.load(store.save()); loaded.contact(PLAYER, 4000);
        assertTrue(loaded.commitment(PLAYER).blocked().contains(EAST));
    }
}
