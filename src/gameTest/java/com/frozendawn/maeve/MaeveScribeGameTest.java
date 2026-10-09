package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.ScribeRecordContents;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** §9.4b Scribe: gate, single claim, lifecycle, frozen drops and local watch/flee/defense behavior. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveScribeGameTest {
    private static final UUID WITNESS = new UUID(0x5C81BEL, 1);

    /** Encounter-separated witnessed evidence through the real store, as ordinary observers would record it. */
    static long train(MaeveObservationGameTest.Scene scene, UUID player, long from, String pattern, int supports, int contradictions) {
        var store = MaeveSavedData.get(scene.server).store();
        String dimension = scene.level.dimension().location().toString();
        long tick = from;
        for (int i = 0; i < supports + contradictions; i++) {
            tick += BeliefPolicy.ENCOUNTER_GAP + 1;
            store.record(player, WITNESS, dimension, scene.origin, tick, pattern, i < supports, "QA_WITNESSED");
        }
        return tick;
    }

    static long gate(MaeveObservationGameTest.Scene scene, UUID player) {
        MaeveDirector.snapshot(scene.server, player);
        long t = train(scene, player, scene.gameTime, BeliefStore.SWORD, 4, 0);
        t = train(scene, player, t, BeliefStore.RANGED, 4, 0);
        t = train(scene, player, t, BeliefStore.RECOVERY, 4, 0);
        scene.clock(t);
        return t;
    }

    static void floor(MaeveObservationGameTest.Scene scene, int size) {
        for (int x = 0; x <= size; x++) for (int z = 0; z <= size; z++) scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
    }

    static List<ItemStack> drops(MaeveObservationGameTest.Scene scene, ArchitectEntity actor) {
        var items = scene.level.getEntitiesOfClass(ItemEntity.class, actor.getBoundingBox().inflate(4));
        scene.entities.addAll(items);
        return items.stream().map(ItemEntity::getItem).toList();
    }

    static void tick(MaeveObservationGameTest.Scene scene, ArchitectEntity actor, long from, int ticks) {
        for (int i = 0; i < ticks && !actor.isRemoved(); i++) { scene.clock(from + i); actor.tick(); }
    }

    /** The MACS Scribe Check world ships exactly these functions; each must parse at the integrated server's level 2. */
    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 40)
    public static void scribePlaytestFunctionsParseAtPermissionTwo(GameTestHelper helper) {
        assertPlaytestFunctionsParse(helper, "macs_scribe", 41);
    }

    /** The natural MACS Scribe Base world: same rule. */
    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 40)
    public static void scribeBaseFunctionsParseAtPermissionTwo(GameTestHelper helper) {
        assertPlaytestFunctionsParse(helper, "macs_scribe_base", 40);
    }

    private static void assertPlaytestFunctionsParse(GameTestHelper helper, String namespace, int count) {
        var server = helper.getLevel().getServer();
        var functions = server.getResourceManager().listResources("function", id ->
                id.getNamespace().equals(namespace) && id.getPath().endsWith(".mcfunction"));
        helper.assertTrue(functions.size() == count, "All " + namespace + " functions must be present: " + functions.size());
        functions.forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                net.minecraft.commands.functions.CommandFunction.fromLines(id, server.getCommands().getDispatcher(),
                        server.createCommandSourceStack().withPermission(2), reader.lines().toList());
            } catch (java.io.IOException | IllegalArgumentException error) {
                helper.fail(namespace + " function must parse at permission level 2: " + id + ": " + error.getMessage());
            }
        });
        helper.succeed();
    }

    /** The checkpoint branches on /fd maeve confidence: whole percent rounded down, read-only, 0 when unknown. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void maeveConfidenceCommandReportsWholePercentWithoutWriting(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 206, scene -> {
            var player = scene.player("scribe_confidence", 3, 3);
            MaeveDirector.snapshot(scene.server, player.getUUID());
            scene.clock(train(scene, player.getUUID(), scene.gameTime, BeliefStore.SWORD, 2, 1));
            var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            var dispatcher = scene.server.getCommands().getDispatcher();
            var before = MaeveDirector.snapshot(scene.server, player.getUUID()).beliefs();
            double sword = before.stream().filter(b -> b.pattern().equals(BeliefStore.SWORD))
                    .mapToDouble(MaeveDirector.BeliefSnapshot::confidence).findFirst().orElse(-1);
            try {
                int percent = dispatcher.execute("fd maeve confidence " + BeliefStore.SWORD.toLowerCase(java.util.Locale.ROOT), source);
                helper.assertTrue(sword > 0 && percent == (int) Math.floor(sword * 100 + 1e-9),
                        "Result is the confidence as a whole percent, rounded down: " + sword + " -> " + percent);
                helper.assertTrue(dispatcher.execute("fd maeve confidence " + BeliefStore.RECOVERY, source) == 0,
                        "An unknown belief reads as 0");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                helper.fail("fd maeve confidence must parse: " + error.getMessage());
            }
            var after = MaeveDirector.snapshot(scene.server, player.getUUID()).beliefs();
            helper.assertTrue(after.size() == before.size() && after.stream().allMatch(b -> before.stream().anyMatch(o ->
                            o.pattern().equals(b.pattern()) && o.evidence() == b.evidence() && o.storedConfidence() == b.storedConfidence())),
                    "Reading a confidence never writes a belief");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeGateAllowsOneClaimAndStopsAfterErased(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 200, scene -> {
            var player = scene.player("scribe_gate", 3, 3);
            MaeveDirector.snapshot(scene.server, player.getUUID());
            long t = train(scene, player.getUUID(), scene.gameTime, BeliefStore.SWORD, 4, 0);
            t = train(scene, player.getUUID(), t, BeliefStore.RANGED, 4, 0);
            scene.clock(t);
            var first = scene.architect(8, 3);
            // The roll comes from the subject; seed 0 rolls 0.73, above the first miss's one-in-eight chance.
            player.getRandom().setSeed(0);
            helper.assertFalse(MaeveDirector.designateScribe(first, player), "Two confident beliefs are not enough to write a record");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream().anyMatch(line -> line.contains("misses=1/8")),
                    "A refused spawn while Maeve knows something is a miss");
            t = train(scene, player.getUUID(), t, BeliefStore.RECOVERY, 4, 0); scene.clock(t);
            helper.assertTrue(MaeveDirector.designateScribe(first, player), "Three confident beliefs open the gate");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream().anyMatch(line -> line.contains("by=GATE") && line.contains("misses=0/8")),
                    "Any designation clears the misses");
            first.becomeScribe();
            helper.assertTrue(first.isScribe() && first.getMainHandItem().is(ModItems.SCRIBE_RECORD.get()), "The Scribe holds its slate");
            var order = MaeveDirector.scribeOrder(first);
            helper.assertTrue(order != null && order.subject().equals(player.getUUID()) && order.watchLabel().equals("ROUTE") && order.watch() == null,
                    "Without an observed shelter it watches routes; no hidden position enters the order: " + order);
            var second = scene.architect(8, 5);
            helper.assertFalse(MaeveDirector.designateScribe(second, player), "One Scribe at a time");
            helper.assertTrue(MaeveDirector.scribeOrder(second) == null && !second.isScribe(), "Only the claimed Architect carries notes");
            helper.assertTrue(MaeveDirector.commitmentHints(first, player).isEmpty(), "A Scribe never takes a counter commitment");
            MaeveDirector.scribeEnded(first, "QA_ENDED");
            helper.assertTrue(MaeveDirector.scribeOrder(first) == null, "An ended claim releases its notes");
            helper.assertFalse(MaeveDirector.designateScribe(second, player), "The next Scribe waits for the cooldown");
            scene.clock(t + ScribePolicy.COOLDOWN);
            var master = scene.architect(8, 7); master.bindToHearthMasterArchitect(UUID.randomUUID(), scene.origin, 0);
            helper.assertFalse(MaeveDirector.designateScribe(master, player), "Masters never participate");
            player.setGameMode(GameType.CREATIVE);
            helper.assertFalse(MaeveDirector.designateScribe(second, player), "Creative players are never a subject");
            player.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(MaeveDirector.designateScribe(second, player), "After the cooldown another natural spawn may be designated");
            second.becomeScribe();
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertTrue(MaeveDirector.scribeOrder(second) == null, "ERASED ends the claim immediately");
            second.kill();
            helper.assertTrue(drops(scene, second).stream().noneMatch(s -> s.is(ModItems.SCRIBE_RECORD.get()) || s.is(Items.FILLED_MAP)),
                    "No record can be written from an erased store");
            var third = scene.architect(8, 9);
            helper.assertFalse(MaeveDirector.designateScribe(third, player), "Scribes stop appearing after ERASED");
            PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertFalse(MaeveDirector.designateScribe(third, player), "Debug reversal starts empty and cannot reopen the gate");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream().noneMatch(line -> line.contains(second.getUUID().toString())),
                    "ERASED leaves no trace of the former Scribe in diagnostics");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeBadLuckProtectionDesignatesByTheEighthMiss(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 202, scene -> {
            var player = scene.player("scribe_unlucky", 3, 3); UUID id = player.getUUID();
            var stranger = scene.player("scribe_stranger", 3, 5);
            MaeveDirector.snapshot(scene.server, id);
            helper.assertFalse(MaeveDirector.designateScribe(scene.architect(8, 3), stranger), "Nothing known, no Scribe");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, id).stream().anyMatch(line -> line.contains("TOO_FEW_CONFIDENT_BELIEFS")
                    && line.contains("misses=0/8")), "A refusal while Maeve knows nothing is not a miss");
            // One confident belief, as for a player whose other habits keep changing.
            long t = train(scene, id, scene.gameTime, BeliefStore.SWORD, 4, 0);
            t = train(scene, id, t, "RETREAT_BEARING_E", 2, 2);
            scene.clock(t);
            ArchitectEntity scribe = null; int spawns = 0;
            while (scribe == null && spawns < ScribePolicy.PITY_MISSES) {
                var actor = scene.architect(8, 3); spawns++;
                if (MaeveDirector.designateScribe(actor, player)) scribe = actor;
                else helper.assertTrue(MaeveDirector.diagnostics(scene.server, id).stream().anyMatch(line -> line.contains("MISS ")),
                        "Each refused natural spawn is counted");
            }
            helper.assertTrue(scribe != null, "The eighth miss is guaranteed");
            final int at = spawns;
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, id).stream().anyMatch(line -> line.contains("by=PITY_" + at + "/8")
                    && line.contains("misses=0/8")), "Designated by bad luck protection, and the count resets");
            scribe.becomeScribe(); scribe.kill();
            var record = drops(scene, scribe).stream().filter(s -> s.is(ModItems.SCRIBE_RECORD.get())).findFirst().orElse(null);
            var contents = record == null ? null : record.get(ModDataComponents.SCRIBE_RECORD.get());
            helper.assertTrue(contents != null && contents.lines().stream().map(ScribeRecordContents.Line::certainty).toList()
                    .equals(List.of("FLAT", "INCONCLUSIVE")), "The unsettled exit is marked inconclusive: " + contents);
            MaeveDirector.scribeEnded(scribe, "QA_ENDED");
            var next = scene.architect(8, 5);
            helper.assertFalse(MaeveDirector.designateScribe(next, player), "Misses wait for the cooldown");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, id).stream().anyMatch(line -> line.contains("COOLDOWN") && line.contains("misses=0/8")),
                    "The cooldown counts no misses");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeDeathDropsFrozenRecordAndMarkedMap(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 201, scene -> {
            var player = scene.player("scribe_subject", 3, 3); UUID id = player.getUUID();
            MaeveDirector.snapshot(scene.server, id);
            long t = train(scene, id, scene.gameTime, BeliefStore.SWORD, 5, 0);
            t = train(scene, id, t, "RETREAT_BEARING_E", 4, 0);
            t = train(scene, id, t, BeliefStore.PURSUIT, 4, 0);
            t = train(scene, id, t, BeliefStore.RANGED, 3, 0);
            t = train(scene, id, t, BeliefStore.RECOVERY, 4, 1);
            t = train(scene, id, t, "RETREAT_BEARING_W", 1, 0);
            var world = MaeveSavedData.get(scene.server).store().world(id);
            String dim = scene.level.dimension().location().toString();
            world.sample(WITNESS, dim, scene.origin.offset(2, 0, 2), true, t);
            world.sample(WITNESS, dim, scene.origin.offset(4, 0, 2), true, t + 1);
            var evidence = new ObservedEvidence(WITNESS, UUID.randomUUID(), dim, scene.origin, t, "QA_WITNESSED", true);
            world.access(dim, scene.origin.offset(6, 0, 2), scene.origin.offset(5, 0, 2), evidence);
            world.event("DANGER_ZONE", dim, scene.origin.offset(9, 0, 9), evidence);
            world.event("HEAT_SOURCE", dim, scene.origin.offset(3, 0, 3), evidence);
            scene.clock(t + 2);
            BlockPos centroid = world.center(dim);
            var actor = scene.architect(8, 8);
            helper.assertTrue(MaeveDirector.designateScribe(actor, player), "Gate: several confident beliefs");
            actor.becomeScribe();
            helper.assertTrue("OPENING".equals(MaeveDirector.scribeOrder(actor).watchLabel())
                    && scene.origin.offset(6, 0, 2).equals(MaeveDirector.scribeOrder(actor).watch()), "Watches the remembered opening");
            actor.kill();
            var drops = drops(scene, actor);
            var record = drops.stream().filter(s -> s.is(ModItems.SCRIBE_RECORD.get())).findFirst().orElse(null);
            helper.assertTrue(record != null, "Death drops the record: " + drops);
            var contents = record.get(ModDataComponents.SCRIBE_RECORD.get());
            helper.assertTrue(contents != null && contents.lines().stream().map(ScribeRecordContents.Line::pattern).toList().equals(List.of(
                    BeliefStore.SWORD, BeliefStore.PURSUIT, "RETREAT_BEARING_E", BeliefStore.RANGED, BeliefStore.RECOVERY)),
                    "At most five beliefs, highest confidence first: " + contents);
            helper.assertTrue(contents.lines().stream().map(ScribeRecordContents.Line::certainty).toList()
                    .equals(List.of("ALWAYS", "FLAT", "FLAT", "HEDGED", "INCONCLUSIVE")), "Confidence is phrasing");
            helper.assertTrue(contents.lines().get(3).thaeven().equals("Eth orren…"), "The weak belief is written exactly as held");
            helper.assertTrue(contents.lines().get(4).thaeven().equals("Mor vel-thaeven. Liss."), "The belief seen both ways is unsettled");
            helper.assertTrue(contents.lines().get(2).thaeven().equals("Vel-sorr aren thaeven."), "Verb last, no tense");
            helper.assertFalse(contents.toString().contains(player.getGameProfile().getName()), "Vel-thae, never the username");
            helper.assertTrue(contents.subject().equals(java.util.Optional.of(player.getUUID())), "Kept by UUID for the chalk portrait");
            var legacy = ScribeRecordContents.Line.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, contents.lines()).getOrThrow();
            var reread = ScribeRecordContents.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, legacy).getOrThrow();
            var saved = ScribeRecordContents.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, contents).getOrThrow();
            helper.assertTrue(reread.lines().equals(contents.lines()) && reread.subject().isEmpty()
                    && ScribeRecordContents.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow().equals(contents),
                    "Records dropped before the subject still read; new ones round-trip");
            var map = drops.stream().filter(s -> s.is(Items.FILLED_MAP)).findFirst().orElse(null);
            helper.assertTrue(map != null, "Death drops the marked map");
            var data = MapItem.getSavedData(map, scene.level);
            helper.assertTrue(data != null && data.locked && data.centerX == centroid.getX() && data.centerZ == centroid.getZ(),
                    "Locked map centered on her shelter estimate " + centroid);
            var marks = map.get(DataComponents.MAP_DECORATIONS).decorations().values();
            helper.assertTrue(marks.size() == 3 && marks.stream().anyMatch(m -> m.type().equals(MapDecorationTypes.BLUE_BANNER))
                    && marks.stream().anyMatch(m -> m.type().equals(MapDecorationTypes.RED_X))
                    && marks.stream().anyMatch(m -> m.type().equals(MapDecorationTypes.TARGET_POINT)), "Openings, losses and heat: " + marks);
            helper.assertTrue(map.get(DataComponents.LORE).lines().size() == 3, "Legend in the same register");
            helper.assertTrue(data.scale == 0 && marks.stream().allMatch(m -> Math.abs(m.x() - data.centerX) <= 56 && Math.abs(m.z() - data.centerZ) <= 56)
                    && marks.stream().anyMatch(m -> Math.abs(m.x() - data.centerX) > 8 || Math.abs(m.z() - data.centerZ) > 8)
                    && data.colors[64 + 64 * 128] != 0, "Zooms in to fit its marks: " + marks);
            // Snapshot rule: new evidence and ERASED never reach a dropped record or map.
            byte[] colors = data.colors.clone();
            train(scene, id, t + 2, BeliefStore.RANGED, 2, 0);
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertTrue(contents.equals(record.get(ModDataComponents.SCRIBE_RECORD.get())), "Records never update after they drop");
            helper.assertTrue(MapItem.getSavedData(map, scene.level) == data && Arrays.equals(colors, data.colors)
                    && map.get(DataComponents.MAP_DECORATIONS).decorations().size() == 3, "Existing maps stay exactly as they are after ERASED");
            PostMaeveWorldState.setForDebug(scene.server, false);
        });
    }

    /** Name kept for the gate list: it now fights back whenever struck up close, cornered or not. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeWatchesFleesAndFightsOnlyWhenCornered(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 202, 2, scene -> {
            floor(scene, 30);
            var player = scene.player("scribe_watched", 28, 15); UUID id = player.getUUID();
            long t = gate(scene, id);
            var actor = scene.architect(15, 15);
            helper.assertTrue(MaeveDirector.designateScribe(actor, player), "Designated");
            actor.becomeScribe(); actor.tickCount = 80; actor.setOnGround(true); actor.setDeltaMovement(Vec3.ZERO);
            float health = player.getHealth();
            tick(scene, actor, t, 60);
            helper.assertTrue(actor.getTarget() == null && actor.position().distanceTo(scene.position(15, 15)) < 1.5,
                    "Watches from a distance without engaging: " + actor.position());
            helper.assertTrue(actor.isScribeWriting(), "Writes on its slate while it watches (presentation only)");
            player.setPos(scene.position(21, 15));
            double before = actor.distanceTo(player);
            tick(scene, actor, t + 60, 80);
            helper.assertTrue(actor.distanceTo(player) > before + 3 && actor.getTarget() == null && player.getHealth() == health,
                    "Flees when approached and never initiates: " + before + " -> " + actor.distanceTo(player));
            helper.assertFalse(actor.isScribeWriting(), "Stops writing to flee");
            var held = MaeveDirector.snapshot(scene.server, id).beliefs().stream()
                    .map(b -> b.pattern() + " " + b.evidence() + "/" + b.contradictions()).toList();
            player.setPos(actor.position().add(8, 0, 0));
            scene.hit(actor, player, true, 1);
            helper.assertTrue(actor.getTarget() == null, "Struck from range, it keeps fleeing");
            // Owner, 2026-10-08: "flee, but fight back if hit". Open ground, a clear way out, struck up close.
            player.setPos(actor.position().add(2, 0, 0));
            scene.hit(actor, player, false, 1);
            helper.assertTrue(actor.getTarget() == player, "Struck up close, it fights back even with a way out (§9.13a local defense)");
            helper.assertTrue(actor.getMainHandItem().is(ModItems.SCRIBE_RECORD.get()), "Still holding the slate, not a weapon");
            var after = MaeveDirector.snapshot(scene.server, id).beliefs().stream()
                    .map(b -> b.pattern() + " " + b.evidence() + "/" + b.contradictions()).toList();
            // Owner, 2026-10-08: sword hits on the Scribe itself knocked a held bow belief off the record.
            helper.assertTrue(after.equals(held), "Hits on the Scribe report no evidence: " + held + " -> " + after);
            tick(scene, actor, t + 400, 5);
            helper.assertTrue(actor.getTarget() == null && actor.isScribe(), "Without fresh damage, defense ends and it resumes fleeing");
        });
    }

    /** Owner, 2026-10-08: with no remembered place it watched from its spawn, too far away; it now keeps a stand-off. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeWithoutAPlaceClosesToAStandOffFromTheSubject(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 207, 3, scene -> {
            floor(scene, 44);
            var player = scene.player("scribe_standoff", 2, 22); UUID id = player.getUUID();
            long t = gate(scene, id);
            var actor = scene.architect(42, 22);
            helper.assertTrue(MaeveDirector.designateScribe(actor, player), "Designated");
            helper.assertTrue(MaeveDirector.scribeOrder(actor).watch() == null, "No remembered opening or shelter");
            actor.becomeScribe(); actor.tickCount = 80; actor.setOnGround(true); actor.setDeltaMovement(Vec3.ZERO);
            tick(scene, actor, t + 1, 400);
            double distance = actor.distanceTo(player);
            helper.assertTrue(distance >= 12 && distance <= 20 && actor.getTarget() == null && actor.isScribeWriting(),
                    "Closes from 40 to a stand-off outside flee range and writes there: " + distance + " " + actor.position());
        });
    }

    /** Owner pass, 2026-10-08: walked up to in a birch wood, it stopped writing but never left its ledge. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeFleesOffALedgeThroughWoods(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 208, 2, scene -> {
            floor(scene, 30);
            for (int x = 1; x < 30; x += 3) for (int z = 1 + x % 2; z < 30; z += 3) {
                for (int y = 0; y < 5; y++) scene.block(x, y, z, Blocks.BIRCH_LOG.defaultBlockState());
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    scene.block(x + dx, 5, z + dz, Blocks.BIRCH_LEAVES.defaultBlockState());
            }
            scene.block(15, 0, 15, Blocks.STONE.defaultBlockState());
            var player = scene.player("scribe_woods", 15, 28); UUID id = player.getUUID();
            long t = gate(scene, id);
            var actor = scene.architect(15, 15);
            actor.setPos(actor.getX(), actor.getY() + 1, actor.getZ());
            helper.assertTrue(MaeveDirector.designateScribe(actor, player), "Designated");
            actor.becomeScribe(); actor.tickCount = 80; actor.setDeltaMovement(Vec3.ZERO);
            tick(scene, actor, t + 1, 40);
            helper.assertTrue(actor.isScribeWriting(), "Settles on its ledge and writes: " + actor.position());
            player.setPos(scene.position(15, 10));
            double before = actor.distanceTo(player);
            tick(scene, actor, t + 41, 160);
            helper.assertTrue(actor.distanceTo(player) > before + 4 && actor.getTarget() == null,
                    "Leaves the ledge and gains ground through the trees: " + before + " -> " + actor.distanceTo(player) + " " + actor.position());
        });
    }

    /** Found in the live Scribe Check: a fresh controller must not treat its spawn as a stalled route. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeWalksOutToARingPostAroundTheRememberedOpening(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 204, 3, scene -> {
            floor(scene, 44);
            var player = scene.player("scribe_travel", 2, 2); UUID id = player.getUUID();
            long t = gate(scene, id);
            var world = MaeveSavedData.get(scene.server).store().world(id);
            String dim = scene.level.dimension().location().toString();
            world.sample(WITNESS, dim, scene.origin.offset(8, 0, 10), true, t);
            var evidence = new ObservedEvidence(WITNESS, UUID.randomUUID(), dim, scene.origin, t, "QA_WITNESSED", true);
            BlockPos opening = scene.origin.offset(10, 0, 10);
            world.access(dim, opening, scene.origin.offset(9, 0, 10), evidence);
            var actor = scene.architect(40, 40);
            helper.assertTrue(MaeveDirector.designateScribe(actor, player), "Designated");
            helper.assertTrue("OPENING".equals(MaeveDirector.scribeOrder(actor).watchLabel()), "Watches the remembered opening");
            actor.becomeScribe(); actor.tickCount = 80; actor.setOnGround(true); actor.setDeltaMovement(Vec3.ZERO);
            double start = Math.sqrt(actor.blockPosition().distSqr(opening));
            tick(scene, actor, t + 1, 420);
            double end = Math.sqrt(actor.blockPosition().distSqr(opening));
            helper.assertTrue(start - end >= 12 && end >= 15 && end <= 26 && actor.getTarget() == null,
                    "Walks from " + start + " to a ring post about 20 blocks out, without engaging; ended at " + end + " " + actor.position());
        });
    }

    /** Live Scribe Check terrain: uneven late-phase snow layers with full-block drifts the route must avoid. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeWalksToItsPostAcrossUnevenLateSnow(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 205, 3, scene -> {
            floor(scene, 44);
            for (int x = 0; x <= 44; x++) for (int z = 0; z <= 44; z++) {
                boolean drift = x % 6 == 3 && z % 5 == 2;
                int layers = 1 + Math.floorMod(x * 7 + z * 3, 5);
                scene.block(x, 0, z, drift ? Blocks.SNOW_BLOCK.defaultBlockState()
                        : Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, layers));
                if (drift) scene.block(x, 1, z, Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 3));
            }
            var player = scene.player("scribe_snow", 2, 2); UUID id = player.getUUID();
            long t = gate(scene, id);
            var world = MaeveSavedData.get(scene.server).store().world(id);
            String dim = scene.level.dimension().location().toString();
            world.sample(WITNESS, dim, scene.origin.offset(8, 0, 10), true, t);
            BlockPos opening = scene.origin.offset(10, 0, 10);
            world.access(dim, opening, scene.origin.offset(9, 0, 10),
                    new ObservedEvidence(WITNESS, UUID.randomUUID(), dim, scene.origin, t, "QA_WITNESSED", true));
            var actor = scene.architect(40, 40);
            actor.setPos(actor.getX(), actor.getY() + 1, actor.getZ());
            helper.assertTrue(MaeveDirector.designateScribe(actor, player), "Designated");
            actor.becomeScribe(); actor.tickCount = 80; actor.setDeltaMovement(Vec3.ZERO);
            for (int i = 0; i < 20; i++) { scene.clock(t + i); actor.tick(); }
            double start = Math.sqrt(actor.blockPosition().distSqr(opening));
            tick(scene, actor, t + 20, 500);
            double end = Math.sqrt(actor.blockPosition().distSqr(opening));
            helper.assertTrue(start - end >= 12 && end >= 15 && end <= 27 && actor.getTarget() == null,
                    "Snow must not strand the Scribe: walked " + start + " -> " + end + " at " + actor.position());
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void scribeLeavesWhenErasedWithoutDroppingNotes(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 203, 2, scene -> {
            floor(scene, 30);
            var player = scene.player("scribe_distant", 0, 0); UUID id = player.getUUID();
            long t = gate(scene, id);
            var actor = scene.architect(26, 26);
            helper.assertTrue(MaeveDirector.designateScribe(actor, player), "Designated");
            actor.becomeScribe(); actor.tickCount = 80; actor.setOnGround(true);
            tick(scene, actor, t, 20);
            helper.assertTrue(!actor.isRemoved() && actor.isScribe(), "A watching Scribe remains while its claim holds");
            PostMaeveWorldState.setForDebug(scene.server, true);
            tick(scene, actor, t + 20, 20);
            helper.assertTrue(actor.isRemoved(), "After ERASED the Scribe leaves rather than lingering");
            helper.assertTrue(drops(scene, actor).isEmpty(), "Leaving is not death; nothing drops");
            PostMaeveWorldState.setForDebug(scene.server, false);
        });
    }
}
