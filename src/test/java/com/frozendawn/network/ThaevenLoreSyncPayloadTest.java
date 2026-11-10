package com.frozendawn.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThaevenLoreSyncPayloadTest {
    @Test
    void bothRevisionArraysRoundTripWithNothingLeftOver() {
        ThaevenLoreSyncPayload sent = new ThaevenLoreSyncPayload(
                0b101L, true, new int[] {-1, 0, 1, -1, 0, 1}, new int[] {1, 1, 0, 0, 0, 0});
        ByteBuf buffer = Unpooled.buffer();
        ThaevenLoreSyncPayload.STREAM_CODEC.encode(buffer, sent);
        ThaevenLoreSyncPayload received = ThaevenLoreSyncPayload.STREAM_CODEC.decode(buffer);

        assertEquals(0, buffer.readableBytes(), "The client reads every byte the server wrote");
        assertEquals(sent.discoveredMask(), received.discoveredMask());
        assertTrue(received.recipeDiscovered());
        assertArrayEquals(sent.seenRevisions(), received.seenRevisions());
        assertArrayEquals(sent.currentRevisions(), received.currentRevisions());
    }
}
