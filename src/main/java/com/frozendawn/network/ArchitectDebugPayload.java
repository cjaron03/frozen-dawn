package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.debug.architect.ArchitectDebugSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Per-operator observations, using the same bounded JSON schema as visual report files. */
public record ArchitectDebugPayload(String json) implements CustomPacketPayload {
    public static final int ROUTES = 1, GEOMETRY = 2, HUD = 4, TRAILS = 8, LABELS = 16;
    public static final int DEFAULT_LAYERS = ROUTES | GEOMETRY | HUD | TRAILS;
    public static final int MAX_JSON_CHARS = 262144;
    public static final Type<ArchitectDebugPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "architect_debug"));
    public static final StreamCodec<FriendlyByteBuf, ArchitectDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> buffer.writeUtf(payload.json, MAX_JSON_CHARS),
            buffer -> new ArchitectDebugPayload(buffer.readUtf(MAX_JSON_CHARS)));

    public record View(boolean enabled, int layers, boolean historical, boolean serverFrozen,
                       ArchitectDebugSnapshot snapshot, String notice) { }

    public static ArchitectDebugPayload of(View view) { return new ArchitectDebugPayload(ArchitectDebugSnapshot.JSON.toJson(view)); }
    public static ArchitectDebugPayload off() { return of(new View(false, 0, false, false, null, "")); }
    public View view() { return ArchitectDebugSnapshot.JSON.fromJson(json, View.class); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
