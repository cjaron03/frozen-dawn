package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** ORSA Helion Confinement Test Facility: a buried, frozen-in hall around a cracked dead tokamak that holds the
 * world's only Confinement Cores. Own batch: the site and its ice jacket reach well past the template. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HelionSiteGameTest {
    @BeforeBatch(batch = "helion_site")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    private static BlockPos build(GameTestHelper h) {
        var hall = h.absolutePos(new BlockPos(10, 2, 10));
        HelionSiteBuilder.place(h.getLevel(), hall, hall.getY() + HelionSitePlacement.DEPTH);
        return hall;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "helion_site", timeoutTicks = 100)
    public static void helionSiteHoldsOneCoreSalvageAndTheSealedLog(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var level = h.getLevel(); var hall = build(h); int half = HelionSiteBuilder.HALF;
            int cores = 0, scrap = 0, tiles = 0; boolean log = false;
            for (var p : BlockPos.betweenClosed(hall.offset(-half, -1, -half), hall.offset(half, HelionSiteBuilder.CEILING, half))) {
                if (level.getBlockState(p).is(ModBlocks.CONFINEMENT_CORE.get())) cores++;
                if (!(level.getBlockEntity(p) instanceof BarrelBlockEntity barrel)) continue;
                for (int i = 0; i < barrel.getContainerSize(); i++) {
                    var s = barrel.getItem(i);
                    if (s.is(ModItems.SUPERCONDUCTING_COIL_SCRAP.get())) scrap += s.getCount();
                    if (s.is(ModItems.FIRST_WALL_TILE.get())) tiles += s.getCount();
                    var lore = s.get(DataComponents.LORE);
                    if (lore != null && lore.lines().stream().anyMatch(l -> l.getString().contains("Ignition achieved, June 13, 2077. Q = 1.8.")))
                        log = true;
                }
            }
            h.assertTrue(level.getBlockState(hall.offset(HelionSiteBuilder.CORE)).is(ModBlocks.CONFINEMENT_CORE.get()) && cores == 1,
                    "One Confinement Core at the heart of the tokamak: " + cores);
            h.assertTrue(scrap == 16 && tiles == 12, "16 Coil Scrap and 12 First-Wall Tiles: " + scrap + ", " + tiles);
            h.assertTrue(log, "The sealed log carries the ignition line");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "helion_site", timeoutTicks = 100)
    public static void helionSiteIsBuriedFrozenInAndCracked(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var level = h.getLevel(); var hall = build(h); int half = HelionSiteBuilder.HALF, ceiling = HelionSiteBuilder.CEILING;
            h.assertTrue(level.getBlockState(hall.offset(half + 1, 3, 0)).is(Blocks.PACKED_ICE)
                    && level.getBlockState(hall.offset(0, ceiling + 1, 0)).is(Blocks.PACKED_ICE), "An ice jacket encases the hall");
            for (var p : HelionSiteBuilder.BREACH) h.assertTrue(level.getBlockState(hall.offset(p)).isAir(), "The crack reaches the core at " + p);
            var shaft = HelionSiteBuilder.SHAFT; int top = HelionSitePlacement.DEPTH;
            h.assertTrue(level.getBlockState(hall.offset(shaft.getX(), top, shaft.getZ())).is(Blocks.IRON_TRAPDOOR), "A hatch marks the shaft at the surface");
            h.assertTrue(level.getBlockState(hall.offset(shaft.getX(), top - 1, shaft.getZ())).is(Blocks.PACKED_ICE), "Ice plugs the shaft");
            for (int y = 0; y < top - HelionSiteBuilder.PLUG; y++)
                h.assertTrue(level.getBlockState(hall.offset(shaft.getX(), y, shaft.getZ())).is(Blocks.LADDER), "Ladder down the shaft at y+" + y);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "helion_site", timeoutTicks = 100)
    public static void takingTheCoreMarksTheSiteLooted(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var level = h.getLevel(); var hall = build(h);
            var state = HelionSiteState.get(level.getServer());
            state.markBuilt(hall.getX() >> 4, hall.getZ() >> 4, hall);
            h.assertTrue(state.unlooted().contains(hall), "A fresh site is unlooted");
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "helion_looter"));
            player.gameMode.destroyBlock(hall.offset(HelionSiteBuilder.CORE));
            h.assertTrue(!state.unlooted().contains(hall), "Taking the core loots the site");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "helion_site", timeoutTicks = 100)
    public static void helionSitesAreRarerThanReactorSites(GameTestHelper h) {
        int helion = 0;
        for (int x = 0; x < 40; x++) for (int z = 0; z < 25; z++) if (HelionSitePlacement.siteChunk(1234L, x, z) != null) helion++;
        h.assertTrue(helion > 420 && helion < 580, "About half of Helion regions roll a site: " + helion);
        double perChunk = 0.5 / ((double) HelionSitePlacement.REGION_SIZE * HelionSitePlacement.REGION_SIZE),
                reactor = ReactorSitePlacement.ROLL_PERCENT / 100.0 / ((double) ReactorSitePlacement.REGION_SIZE * ReactorSitePlacement.REGION_SIZE);
        h.assertTrue(perChunk < reactor, "Rarer per chunk than CFU-7 sites");
        h.assertTrue(java.util.Arrays.equals(HelionSitePlacement.siteChunk(99L, 3, -2), HelionSitePlacement.siteChunk(99L, 3, -2)), "Deterministic");
        h.succeed();
    }
}
