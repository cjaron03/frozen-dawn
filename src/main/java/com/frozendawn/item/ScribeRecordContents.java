package com.frozendawn.item;

import com.frozendawn.maeve.MaeveDirector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * §9.4b frozen field notes. Written once from Maeve's store at the Scribe's death and never updated:
 * the item keeps its own copy, so records survive ERASED exactly as dropped (owner decision, 2026-10-06).
 * The subject is kept by UUID only, for the chalk portrait; the record never holds the username.
 */
public record ScribeRecordContents(List<Line> lines, Optional<UUID> subject) {
    public static final int MAX_LINES = 5;
    public static final String SUBJECT = "Vel-thae.";

    public record Line(String pattern, String thaeven, String translation, List<String> arguments, String certainty) {
        public static final Codec<Line> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("pattern").forGetter(Line::pattern),
                Codec.STRING.fieldOf("thaeven").forGetter(Line::thaeven),
                Codec.STRING.fieldOf("translation").forGetter(Line::translation),
                Codec.STRING.listOf().optionalFieldOf("arguments", List.of()).forGetter(Line::arguments),
                Codec.STRING.fieldOf("certainty").forGetter(Line::certainty)).apply(instance, Line::new));
        public static final StreamCodec<ByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Line::pattern,
                ByteBufCodecs.STRING_UTF8, Line::thaeven,
                ByteBufCodecs.STRING_UTF8, Line::translation,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4)), Line::arguments,
                ByteBufCodecs.STRING_UTF8, Line::certainty,
                Line::new);

        public Line { arguments = List.copyOf(arguments.subList(0, Math.min(4, arguments.size()))); }

        /** Confidence is phrasing: a flat statement when certain, "Always." when very certain, "Perhaps." when not. */
        public Component translated() {
            MutableComponent line = Component.translatable(translation, arguments.stream().map(Component::translatable).toArray());
            return switch (certainty) {
                case "ALWAYS" -> line.append(" ").append(Component.translatable("record.frozendawn.scribe.certainty.always"));
                case "HEDGED" -> line.append(" ").append(Component.translatable("record.frozendawn.scribe.certainty.hedged"));
                case "INCONCLUSIVE" -> line.append(" ").append(Component.translatable("record.frozendawn.scribe.certainty.inconclusive"));
                default -> line;
            };
        }
    }

    // Records dropped before the subject was kept are a bare list of lines; they still read, without a portrait.
    public static final Codec<ScribeRecordContents> CODEC = Codec.withAlternative(
            RecordCodecBuilder.create(instance -> instance.group(
                    Line.CODEC.listOf().fieldOf("lines").forGetter(ScribeRecordContents::lines),
                    UUIDUtil.CODEC.optionalFieldOf("subject").forGetter(ScribeRecordContents::subject))
                    .apply(instance, ScribeRecordContents::new)),
            Line.CODEC.listOf().xmap(lines -> new ScribeRecordContents(lines, Optional.empty()), ScribeRecordContents::lines));
    public static final StreamCodec<ByteBuf, ScribeRecordContents> STREAM_CODEC = StreamCodec.composite(
            Line.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), ScribeRecordContents::lines,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), ScribeRecordContents::subject,
            ScribeRecordContents::new);

    public ScribeRecordContents { lines = List.copyOf(lines.subList(0, Math.min(MAX_LINES, lines.size()))); }

    static ScribeRecordContents of(MaeveDirector.ScribeNotes notes) {
        return new ScribeRecordContents(notes.notes().stream().map(n -> new Line(n.pattern(), n.thaeven(), n.translation(),
                n.arguments(), n.certainty())).toList(), Optional.of(notes.subject()));
    }
}
