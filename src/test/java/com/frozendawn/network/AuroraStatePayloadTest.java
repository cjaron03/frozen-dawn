package com.frozendawn.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AuroraStatePayloadTest {
    @Test void clockDomainAndEventIdentitySurviveTransport() {
        for (boolean preview : new boolean[]{false, true}) {
            var expected = new AuroraStatePayload(Long.MIN_VALUE + 71, 4_800_000, 6000, 0.73F, preview);
            var buffer = Unpooled.buffer();
            try {
                AuroraStatePayload.STREAM_CODEC.encode(buffer, expected);
                assertEquals(expected, AuroraStatePayload.STREAM_CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
}
