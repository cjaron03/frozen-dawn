package com.frozendawn.descent;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DescentGameTest {
    private static final BlockPos AT = new BlockPos(4, 1, 4);

    @BeforeBatch(batch = "descent")
    public static void report(ServerLevel level) {
        GameTestReporting.installReporter(level);
    }

    /** Spamming an instrument never stacks the melody: one clip until the clip has run out. */
    @GameTest(template = GameTestTemplates.EMPTY, batch = "descent", timeoutTicks = 400)
    public static void anInstrumentPlaysOnce(GameTestHelper h) {
        DescentEra era = DescentEra.GLACIAL_MAXIMUM;
        h.setBlock(AT, ModBlocks.DESCENT_BONE_FLUTE.get());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        Ear ear = listen(h);
        h.useBlock(AT, player);
        h.useBlock(AT, player);
        h.runAfterDelay(era.clipTicks() - 2, () -> h.useBlock(AT, player));
        h.runAfterDelay(era.clipTicks() + 2, () -> {
            try {
                h.assertTrue(ear.count(era) == 1, "three uses inside one clip play it once: " + ear.count(era));
                h.useBlock(AT, player);
                h.assertTrue(ear.count(era) == 2, "a use after the clip has run out plays it again: " + ear.count(era));
            } finally {
                ear.close();
            }
            h.succeed();
        });
    }

    /** No redstone plays an instrument: they are not note blocks. */
    @GameTest(template = GameTestTemplates.EMPTY, batch = "descent", timeoutTicks = 40)
    public static void instrumentsAreNotNoteBlocks(GameTestHelper h) {
        h.setBlock(AT, ModBlocks.DESCENT_CHURCH_BELL.get());
        Ear ear = listen(h);
        h.setBlock(AT.east(), Blocks.REDSTONE_BLOCK);
        h.setBlock(AT.west(), Blocks.REDSTONE_BLOCK);
        h.setBlock(AT.east(), Blocks.AIR);
        h.runAfterDelay(20, () -> {
            try {
                h.assertTrue(ear.heard.stream().noneMatch(DescentGameTest::isDescent), "a powered instrument stays silent: " + ear.heard);
                h.assertBlockPresent(ModBlocks.DESCENT_CHURCH_BELL.get(), AT);
            } finally {
                ear.close();
            }
            h.succeed();
        });
    }

    /** Every motif, the scratches and the portrait load as painting variants and reach the creative tab. */
    @GameTest(template = GameTestTemplates.EMPTY, batch = "descent", timeoutTicks = 20)
    public static void everyDescentPaintingLoads(GameTestHelper h) {
        var stacks = DescentPaintings.creativeStacks(h.getLevel().registryAccess());
        h.assertTrue(stacks.size() == DescentPaintings.IDS.size(),
                "loaded " + stacks.size() + " of " + DescentPaintings.IDS.size() + " descent paintings");
        h.succeed();
    }

    /** Every sound played within two blocks of the instrument while the test listens. */
    private record Ear(List<SoundEvent> heard, Consumer<PlayLevelSoundEvent.AtPosition> listener) {
        long count(DescentEra era) {
            return heard.stream().filter(sound -> sound == era.sound().value()).count();
        }

        void close() {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    private static Ear listen(GameTestHelper h) {
        List<SoundEvent> heard = new ArrayList<>();
        BlockPos at = h.absolutePos(AT);
        Consumer<PlayLevelSoundEvent.AtPosition> listener = e -> {
            if (e.getLevel() == h.getLevel() && e.getSound() != null && e.getPosition().distanceToSqr(at.getCenter()) < 4) {
                heard.add(e.getSound().value());
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        return new Ear(heard, listener);
    }

    private static boolean isDescent(SoundEvent sound) {
        for (DescentEra era : DescentEra.values()) if (era.sound().value() == sound) return true;
        return false;
    }
}
