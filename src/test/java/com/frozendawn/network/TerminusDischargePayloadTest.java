package com.frozendawn.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** A discharge into a crowd still fits on the wire: past the cap, further minds go without a mote. */
class TerminusDischargePayloadTest {
    @Test
    void aCrowdIsCappedAndStillEncodes() {
        var minds = new ArrayList<Vector3f>();
        for (int i = 0; i < 200; i++) minds.add(new Vector3f(i, 64, 0));
        var payload = TerminusDischargePayload.of(7, minds);
        assertEquals(TerminusDischargePayload.MAX_MINDS, payload.minds().size());
        var buffer = Unpooled.buffer();
        TerminusDischargePayload.STREAM_CODEC.encode(buffer, payload);
        var decoded = TerminusDischargePayload.STREAM_CODEC.decode(buffer);
        assertEquals(7, decoded.operator());
        assertEquals(TerminusDischargePayload.MAX_MINDS, decoded.minds().size());
    }
}
