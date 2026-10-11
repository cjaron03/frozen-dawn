package com.frozendawn.labbridge;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Exact stopped movement attempt, bound before a distinct one-use lab continuation. */
public final class LabNaturalRepeatProof {
    private static final UUID PLAYER = UUID.fromString("380df991-f603-344c-a090-369bad2a924a");
    private static final Map<String, String> FILES = Map.ofEntries(
            Map.entry("preparation.json", "1f34699ff67845516ac7044d06a39f878b852242c5a9b373a8acad692e9d0c5d"),
            Map.entry("start.json", "9e77f8401d84f965575f05801cf0beca7eea750f6915328be56a197f4b0cef88"),
            Map.entry("frames.jsonl", "b5c1aa348bcb75c4805b1c54f0c338091dd94d722db8d8042a7462095d8bdf7d"),
            Map.entry("end.jsonl", "451348cf833fffd680b0359f2ac21f08e0b9e987e1ae84cf492d167e9984d380"),
            Map.entry("fastwait/frames.jsonl", "457c95dbd03cc023931a7973540a8c1356bd35efbe558c18594d5c8d229770bf"),
            Map.entry("fastwait/waiting.jsonl", "af252da18932820939bb83d6ebbe0d8c1d807cd493b133f6cbb015ce7b2d9fe0"),
            Map.entry("fastwait/end.jsonl", "0aea95629132233c17668c901b89c00f7e6e945248d09736be278b77eeb8f851"),
            Map.entry("fastwait/continuation.json", "fb48e53fea744c92f07d7ee70e2f2a073a2b35c16384a2b70890589c2108dee7"));
    private LabNaturalRepeatProof() { }
    public static boolean verify(Path root, UUID player) {
        if (!PLAYER.equals(player) || Files.exists(root.resolve("fastwait/arrivals.jsonl"))) return false;
        try {
            for (var entry : FILES.entrySet()) {
                var path = root.resolve(entry.getKey());
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))).equals(entry.getValue())) return false;
            }
            var endings = Files.readAllLines(root.resolve("fastwait/end.jsonl"));
            if (endings.size() != 1) return false;
            var end = JsonParser.parseString(endings.getFirst()).getAsJsonObject();
            return end.get("player").getAsString().equals(player.toString()) && end.get("arrivals").getAsInt() == 0
                    && end.get("outcome").getAsString().equals("INCONCLUSIVE_NO_BLIND_ARRIVAL")
                    && end.get("waitingPhase").getAsString().equals("HELD")
                    && end.get("waitingReason").getAsString().equals("PLAYER_OR_CONTEXT_CHANGED")
                    && end.get("frozen").getAsBoolean() && !end.get("sprinting").getAsBoolean();
        } catch (Exception e) { return false; }
    }
}
