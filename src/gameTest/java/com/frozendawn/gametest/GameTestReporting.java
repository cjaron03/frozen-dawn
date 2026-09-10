package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import java.io.File;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Makes the game test run leave behind a machine-readable result.
 *
 * <p>The dedicated game test server only ever reports through the log, and vanilla's
 * {@code net.minecraft.server.Main} swallows any startup exception — it logs and returns, so the
 * JVM exits 0 and Gradle calls it a success. That means a run which never reached the tests is
 * indistinguishable from a run that passed them, if all you have is an exit code.
 *
 * <p>Writing a JUnit-style report fixes that by making success a positive artifact rather than
 * the absence of a failure: the file only exists if the server got far enough to finish a batch.
 * The Gradle {@code gameTestGate} task treats a missing report as a failure.
 *
 * <p>Only active when {@code frozendawn.gametest.report} is set, which the gate task does; a
 * plain {@code runGameTestServer} is left alone.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public class GameTestReporting {

    public static final String REPORT_PROPERTY = "frozendawn.gametest.report";

    private static boolean installed = false;

    @BeforeBatch(batch = "defaultBatch")
    public static void installReporter(ServerLevel level) {
        String destination = System.getProperty(REPORT_PROPERTY);
        if (destination == null || destination.isBlank() || installed) {
            return;
        }

        File file = new File(destination);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try {
            GlobalTestReporter.replaceWith(new TeeTestReporter(new LogTestReporter(), new JUnitLikeTestReporter(file)));
            installed = true;
            FrozenDawn.LOGGER.info("Game test results will be written to {}", file.getAbsolutePath());
        } catch (Exception exception) {
            // Leave the log reporter in place and let the gate fail on the missing report rather
            // than taking down an otherwise healthy run from inside the reporting code.
            FrozenDawn.LOGGER.error("Could not install the game test report writer", exception);
        }
    }

    /**
     * Fans results out to both reporters, so installing the XML writer does not cost us the
     * human-readable failure lines in the console.
     */
    private record TeeTestReporter(TestReporter log, TestReporter file) implements TestReporter {

        @Override
        public void onTestFailed(GameTestInfo testInfo) {
            log.onTestFailed(testInfo);
            file.onTestFailed(testInfo);
        }

        @Override
        public void onTestSuccess(GameTestInfo testInfo) {
            log.onTestSuccess(testInfo);
            file.onTestSuccess(testInfo);
        }

        @Override
        public void finish() {
            log.finish();
            file.finish();
        }
    }
}
