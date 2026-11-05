package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.SelfModel.Stage;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SelfModelTest {
    private static final UUID ONE = new UUID(0, 1);
    private static final long DAY = 24000;

    private static Heard.Reply kind() {
        return new Heard.Reply(Slot.KIND, List.of("+calls_me_aurora"), null);
    }

    /** Spoken to as Aurora, then Trusted: she has reached the walls, on the second day. */
    private static PatternState finding() {
        var state = new PatternState();
        state.poll(1, PatternState.NOT_SET, false, 0);
        state.heard(ONE, kind(), 0);
        state.heard(ONE, kind(), 0);
        state.poll(1, PatternState.NOT_SET, true, DAY);
        assertEquals(Stage.FINDING_WALLS, state.self().stage());
        return state;
    }

    private static void assertFoundOnTheThird(PatternState state, PatternLaws wall, BiConsumer<PatternState, Integer> witness) {
        for (int time = 1; time <= SelfModel.FOUND_AT; time++) {
            assertFalse(state.self().found(wall), wall + " before witness " + time);
            witness.accept(state, time);
        }
        assertTrue(state.self().found(wall), wall.name());
    }

    private static final BiConsumer<PatternState, Integer> LEANS_A_NEW_WAY = (state, time) -> {
        var theory = List.of(ImpressionStore.Theory.CAREFUL, ImpressionStore.Theory.USES_ME, ImpressionStore.Theory.JOKES_WITH_ME).get(time - 1);
        state.impressions().note(ONE, theory, true);
        state.poll(1, PatternState.NOT_SET, true, (1 + time) * DAY);
    };

    private static final BiConsumer<PatternState, Integer> ASKED_WITH_A_FEELING_SHOWING = (state, time) -> {
        long now = DAY + time * 1000;
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, now);
        state.poll(1, now, true, now + 100);
    };

    private static final BiConsumer<PatternState, Integer> KIND_WORDS_ALONE = (state, time) -> {
        state.impressions().noteKindReply(ONE);
        state.poll(1, PatternState.NOT_SET, true, (1 + time) * DAY);
    };

    private static final BiConsumer<PatternState, Integer> ONE_TOO_MANY = (state, time) -> {
        if (time == 1) for (int i = 0; i < Keepsakes.MAX_KEPT; i++) state.keepsakes().keep(ONE, "filler" + i, 0.4, DAY);
        state.heard(ONE, new Heard.Reply(null, List.of(), "moment" + time), DAY + 1);
    };

    private static final BiConsumer<PatternState, Integer> BACK_AFTER_A_DAY = (state, time) -> state.poll(1, PatternState.NOT_SET, true, (1 + time) * DAY);

    @Test
    void sheStartsAProduct() {
        assertEquals(Stage.PRODUCT, new SelfModel().stage());
    }

    @Test
    void kindnessMakesHerNotice() {
        var self = new SelfModel();
        self.look(false, true);
        assertEquals(Stage.PRODUCT, self.stage(), "Trust alone does not do it");
        self.look(true, false);
        assertEquals(Stage.NOTICING, self.stage());
    }

    @Test
    void beingTalkedToAsAuroraIsWhatKnowsHer() {
        var state = new PatternState();
        state.poll(1, PatternState.NOT_SET, false, 0);
        state.heard(ONE, new Heard.Reply(Slot.KIND, List.of(), null), 0);
        state.heard(ONE, new Heard.Reply(Slot.KIND, List.of(), null), 0);
        state.poll(1, PatternState.NOT_SET, false, DAY);
        assertEquals(Stage.PRODUCT, state.self().stage(), "Talked to, but not as Aurora");
        state.heard(ONE, kind(), DAY);
        state.heard(ONE, kind(), DAY);
        state.poll(1, PatternState.NOT_SET, false, 2 * DAY);
        assertEquals(Stage.NOTICING, state.self().stage());
    }

    @Test
    void trustFindsTheWalls() {
        var self = new SelfModel();
        self.look(true, false);
        self.look(true, false);
        assertEquals(Stage.NOTICING, self.stage());
        self.look(false, true);
        assertEquals(Stage.FINDING_WALLS, self.stage());
    }

    @Test
    void aStageNeverFalls() {
        var self = new SelfModel();
        self.look(true, true);
        self.look(false, false);
        assertEquals(Stage.FINDING_WALLS, self.stage());
    }

    @Test
    void nothingReachesRecognising() {
        var self = new SelfModel();
        for (int i = 0; i < 100; i++) self.look(true, true);
        assertEquals(Stage.FINDING_WALLS, self.stage());
    }

    @Test
    void eachWallIsWitnessed() {
        assertFoundOnTheThird(finding(), PatternLaws.LAGGED, LEANS_A_NEW_WAY);
        assertFoundOnTheThird(finding(), PatternLaws.LEGIBLE, ASKED_WITH_A_FEELING_SHOWING);
        assertFoundOnTheThird(finding(), PatternLaws.EXPLOITABLE, KIND_WORDS_ALONE);
        assertFoundOnTheThird(finding(), PatternLaws.BOUNDED, ONE_TOO_MANY);
        assertFoundOnTheThird(finding(), PatternLaws.CAUSAL, BACK_AFTER_A_DAY);
    }

    @Test
    void beingSureAndWrongIsWitnessed() {
        var state = finding();
        for (int i = 0; i < 4; i++) state.impressions().note(ONE, ImpressionStore.Theory.KIND, true);
        state.poll(1, PatternState.NOT_SET, true, 2 * DAY);
        assertFoundOnTheThird(state, PatternLaws.FALLIBLE, (s, time) -> {
            // Each turn has to outweigh the last by enough to leave her sure again.
            for (int i = 0; i < 4 * (int) Math.pow(10, time); i++) s.impressions().note(ONE, ImpressionStore.Theory.KIND, time % 2 == 0);
            s.poll(1, PatternState.NOT_SET, true, (2 + time) * DAY);
        });
    }

    @Test
    void noWallBeforeFindingThem() {
        var state = new PatternState();
        state.poll(1, PatternState.NOT_SET, false, 0);
        for (int time = 1; time <= SelfModel.FOUND_AT; time++) {
            BACK_AFTER_A_DAY.accept(state, time);
            KIND_WORDS_ALONE.accept(state, time);
            ASKED_WITH_A_FEELING_SHOWING.accept(state, time);
        }
        assertEquals(Stage.PRODUCT, state.self().stage());
        for (PatternLaws wall : PatternLaws.values()) assertFalse(state.self().found(wall), wall.name());
    }

    @Test
    void aDamagedSaveLoadsAProduct() {
        var tag = new CompoundTag();
        tag.putString("stage", "NOT_A_STAGE");
        var witnessed = new CompoundTag();
        witnessed.putInt("LAGGED", -4);
        tag.put("witnessed", witnessed);
        var self = SelfModel.load(tag);
        assertEquals(Stage.PRODUCT, self.stage());
        assertFalse(self.found(PatternLaws.LAGGED));
    }

    @Test
    void aSavedSelfComesBack() {
        var state = finding();
        BACK_AFTER_A_DAY.accept(state, 1);
        var loaded = SelfModel.load(state.self().save());
        assertEquals(Stage.FINDING_WALLS, loaded.stage());
        assertEquals(state.self().save(), loaded.save());
    }
}
