package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabReconnectInputTest {
    private static final long TICK = 176071, TIME = 1791616806836L;
    private JsonObject recorded() {
        try (var stream = getClass().getResourceAsStream("/labbridge/reconnect-clock-failure-sample.json")) {
            return JsonParser.parseReader(new InputStreamReader(java.util.Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private JsonObject assess(JsonObject sample) {
        return LabReconnectInput.assess(sample, "25a1fd52-f2b0-417d-b626-f2d941c9e48f", 56264, TICK, TIME);
    }
    private void refuses(JsonObject sample, String reason) {
        var result = assess(sample);
        assertFalse(result.get("accepted").getAsBoolean());
        assertEquals(reason, result.get("reason").getAsString());
    }
    @Test void archivedFreshFourTickLabelLagQualifiesWithoutRewritingEitherClock() {
        var sample = recorded(); String original = sample.toString(); var result = assess(sample);
        assertTrue(result.get("accepted").getAsBoolean());
        assertEquals(4, result.get("hostTickLag").getAsLong());
        assertEquals(44, result.get("sampleAgeMillis").getAsLong());
        assertEquals(130, result.get("anchorAgeMillis").getAsLong());
        assertEquals(original, sample.toString());
        assertEquals(sample, result.getAsJsonObject("sample"));
    }
    @Test void oldPhysicalSampleAndOldHostPublicationBothRefuse() {
        var sample = recorded(); sample.addProperty("sampledAtMillis", TIME - 251); sample.addProperty("hostSampledAtMillis", TIME - 251);
        refuses(sample, "GUEST_INPUT_CLOCK_DELAYED");
        sample = recorded(); sample.addProperty("hostSampledAtMillis", TIME - 251);
        refuses(sample, "GUEST_INPUT_CLOCK_DELAYED");
    }
    @Test void hostTickLagRemainsBoundedEvenWithFreshWallTimes() {
        var sample = recorded(); sample.addProperty("gameTime", TICK - 9);
        refuses(sample, "GUEST_INPUT_CLOCK_DELAYED");
        sample.addProperty("gameTime", TICK - 8); sample.addProperty("sampledAtMillis", TIME - 250); sample.addProperty("hostSampledAtMillis", TIME - 250);
        assertTrue(assess(sample).get("accepted").getAsBoolean());
    }
    @Test void futureTicksFutureSamplesAndReversedAnchorOrderRefuse() {
        var sample = recorded(); sample.addProperty("gameTime", TICK + 1); refuses(sample, "GUEST_INPUT_CLOCK_INVALID");
        sample = recorded(); sample.addProperty("sampledAtMillis", TIME + 1); refuses(sample, "GUEST_INPUT_CLOCK_INVALID");
        sample = recorded(); sample.addProperty("hostSampledAtMillis", TIME); refuses(sample, "GUEST_INPUT_CLOCK_INVALID");
    }
    @Test void quietInputStillRequiresSameCapturePlayerAndRemoteClock() {
        for (String field : new String[]{"captureSession", "player", "playerName", "clockSource"}) {
            var sample = recorded(); sample.addProperty(field, "foreign"); refuses(sample, "GUEST_INPUT_IDENTITY_CHANGED");
        }
        var sample = recorded(); sample.addProperty("remoteConnection", false); refuses(sample, "GUEST_INPUT_IDENTITY_CHANGED");
        sample = recorded(); sample.addProperty("port", 1); refuses(sample, "GUEST_INPUT_IDENTITY_CHANGED");
    }
    @Test void realInputFocusMenusMouseAndMovementAreStillRejected() {
        for (String field : new String[]{"screenOpen", "paused", "sneaking", "flying", "useDown", "attackDown"}) {
            var sample = recorded(); sample.addProperty(field, true); refuses(sample, "GUEST_INPUT_NOT_QUIET");
        }
        var sample = recorded(); sample.addProperty("focused", false); refuses(sample, "GUEST_INPUT_NOT_QUIET");
        sample = recorded(); sample.getAsJsonObject("windowKeys").addProperty("mouse0", true); refuses(sample, "GUEST_INPUT_NOT_QUIET");
        sample = recorded(); sample.getAsJsonObject("mapped").addProperty("forward", true); refuses(sample, "GUEST_INPUT_NOT_QUIET");
        sample = recorded(); sample.getAsJsonObject("input").addProperty("leftImpulse", .1); refuses(sample, "GUEST_INPUT_NOT_QUIET");
    }
    @Test void MissingOrMalformedSamplesAndIncompleteHardwareEvidenceRefuse() {
        refuses(null, "GUEST_INPUT_SAMPLE_MISSING");
        refuses(new JsonObject(), "GUEST_INPUT_SAMPLE_INVALID");
        var sample = recorded(); sample.getAsJsonObject("windowKeys").remove("mouse0"); refuses(sample, "GUEST_INPUT_NOT_QUIET");
    }
    @Test void healthGroundAndPositionRemainMandatory() {
        var sample = recorded(); sample.addProperty("health", 19); refuses(sample, "GUEST_INPUT_SCENE_CHANGED");
        sample = recorded(); sample.addProperty("onGround", false); refuses(sample, "GUEST_INPUT_SCENE_CHANGED");
        sample = recorded(); sample.addProperty("x", 18.6); refuses(sample, "GUEST_INPUT_SCENE_CHANGED");
    }
}
