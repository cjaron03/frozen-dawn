package com.frozendawn.labbridge;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class LabRoomArchiveReplayTest {
    @TempDir Path root;
    @Test void onlyExactMarkedDisposableCopyCanRunTheLoadProof()throws Exception{
        var copy=Files.createDirectory(root.resolve("Power Airlock Archive Load Test"));Files.writeString(copy.resolve(".room-set-archive-replay"),"token");
        var original=Files.createDirectory(root.resolve("Powered Airlock Check"));Files.writeString(original.resolve(".room-set-archive-replay"),"token");
        assertTrue(LabRoomArchiveReplay.matches(copy,copy,"token"));
        assertFalse(LabRoomArchiveReplay.matches(copy,copy,"wrong"));
        assertFalse(LabRoomArchiveReplay.matches(original,copy,"token"));
        assertFalse(LabRoomArchiveReplay.matches(original,original,"token"));
    }
}
