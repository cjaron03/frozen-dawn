import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GenerateColdGuiAssets {

    // Historical/internal utility only. The release provenance for shipped UI
    // replacement assets is documented in ASSETS.md; final assets under
    // assets/minecraft are original Frozen Dawn compatibility textures.
    // Do not use this tool as release asset provenance or to copy/derive
    // Mojang/Microsoft texture assets for distribution.
    private static final Path INPUT_JAR = Path.of("build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar");
    private static final Path OUTPUT_ROOT = Path.of("src/main/resources");
    private static final String CONTAINER_PREFIX = "assets/minecraft/textures/gui/container/";
    private static final String CONTAINER_SPRITE_PREFIX = "assets/minecraft/textures/gui/sprites/container/";

    private GenerateColdGuiAssets() {
    }

    public static void main(String[] args) throws Exception {
        if (!Files.exists(INPUT_JAR)) {
            throw new IllegalStateException("Missing input resources jar: " + INPUT_JAR);
        }

        int generated = 0;
        int copiedMeta = 0;
        int skipped = 0;

        try (ZipFile zip = new ZipFile(INPUT_JAR.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!shouldGenerate(name)) {
                    continue;
                }

                Path output = OUTPUT_ROOT.resolve(name);
                if (Files.exists(output)) {
                    skipped++;
                    continue;
                }

                BufferedImage source;
                try (InputStream stream = zip.getInputStream(entry)) {
                    source = ImageIO.read(stream);
                }

                if (source == null) {
                    continue;
                }

                Files.createDirectories(output.getParent());
                BufferedImage themed = recolor(source, modeFor(name));
                ImageIO.write(themed, "png", output.toFile());
                generated++;

                ZipEntry mcmeta = zip.getEntry(name + ".mcmeta");
                if (mcmeta != null) {
                    Path mcmetaOutput = OUTPUT_ROOT.resolve(mcmeta.getName());
                    if (!Files.exists(mcmetaOutput)) {
                        Files.createDirectories(mcmetaOutput.getParent());
                        try (InputStream stream = zip.getInputStream(mcmeta); OutputStream out = Files.newOutputStream(mcmetaOutput)) {
                            stream.transferTo(out);
                        }
                        copiedMeta++;
                    }
                }
            }
        }

        System.out.println("Generated cold GUI assets: " + generated);
        System.out.println("Copied mcmeta files: " + copiedMeta);
        System.out.println("Skipped existing overrides: " + skipped);
    }

    private static boolean shouldGenerate(String name) {
        if (!name.endsWith(".png")) {
            return false;
        }

        if (name.startsWith(CONTAINER_PREFIX)) {
            return true;
        }

        if (!name.startsWith(CONTAINER_SPRITE_PREFIX)) {
            return false;
        }

        String lower = name.toLowerCase(Locale.ROOT);
        if (containsAny(lower, List.of("error", "out_of_stock", "discount_strikethrough", "redstone", "map.", "scaled_map", "duplicated_map", "locked"))) {
            return false;
        }

        return containsAny(
                lower,
                List.of(
                        "slot",
                        "scroller",
                        "button",
                        "tab_",
                        "progress",
                        "background",
                        "selected",
                        "highlighted",
                        "recipe",
                        "pattern",
                        "enchantment_slot",
                        "level_",
                        "trade_arrow",
                        "experience_bar",
                        "text_field",
                        "armor_slot",
                        "saddle_slot",
                        "chest_slots",
                        "llama_armor_slot"
                )
        );
    }

    private static boolean containsAny(String value, List<String> needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Mode modeFor(String name) {
        if (name.startsWith(CONTAINER_PREFIX)) {
            return Mode.PANEL;
        }

        String lower = name.toLowerCase(Locale.ROOT);
        if (containsAny(lower, List.of("highlighted", "selected", "button", "tab_", "trade_arrow", "experience_bar", "progress"))) {
            return Mode.BRIGHT;
        }

        return Mode.ACCENT;
    }

    private static BufferedImage recolor(BufferedImage source, Mode mode) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < source.getHeight(); y++) {
            float vertical = source.getHeight() <= 1 ? 0.0f : (float) y / (source.getHeight() - 1);
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    target.setRGB(x, y, 0);
                    continue;
                }

                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                float luma = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255.0f;
                float shaped = switch (mode) {
                    case PANEL -> (float) Math.pow(luma, 0.88);
                    case ACCENT -> (float) Math.pow(luma, 0.96);
                    case BRIGHT -> (float) Math.pow(luma, 0.82);
                };

                int[] palette = switch (mode) {
                    case PANEL -> gradient(shaped, 0x050B14, 0x1B3552, 0xB7E6FF);
                    case ACCENT -> gradient(shaped, 0x11233A, 0x4C7BB0, 0xDDF8FF);
                    case BRIGHT -> gradient(shaped, 0x1A3150, 0x76B3E6, 0xF3FDFF);
                };

                float frost = switch (mode) {
                    case PANEL -> vertical < 0.28f ? 0.10f : 0.0f;
                    case ACCENT -> vertical < 0.30f ? 0.07f : 0.0f;
                    case BRIGHT -> vertical < 0.38f ? 0.12f : 0.0f;
                };

                int outRed = mix(palette[0], 235, frost);
                int outGreen = mix(palette[1], 248, frost);
                int outBlue = mix(palette[2], 255, frost);
                target.setRGB(x, y, alpha << 24 | outRed << 16 | outGreen << 8 | outBlue);
            }
        }

        return target;
    }

    private static int[] gradient(float amount, int dark, int mid, int light) {
        if (amount < 0.58f) {
            float local = amount / 0.58f;
            return new int[] {
                    mix((dark >>> 16) & 0xFF, (mid >>> 16) & 0xFF, local),
                    mix((dark >>> 8) & 0xFF, (mid >>> 8) & 0xFF, local),
                    mix(dark & 0xFF, mid & 0xFF, local)
            };
        }

        float local = (amount - 0.58f) / 0.42f;
        return new int[] {
                mix((mid >>> 16) & 0xFF, (light >>> 16) & 0xFF, local),
                mix((mid >>> 8) & 0xFF, (light >>> 8) & 0xFF, local),
                mix(mid & 0xFF, light & 0xFF, local)
        };
    }

    private static int mix(int from, int to, float amount) {
        return from + Math.round((to - from) * amount);
    }

    private enum Mode {
        PANEL,
        ACCENT,
        BRIGHT
    }
}
