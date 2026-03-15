import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

public final class GenerateColdBlockAssets {

    private static final Path INPUT_JAR = Path.of("build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar");
    private static final Path OUTPUT_ROOT = Path.of("src/main/resources/assets/frozendawn/textures/block");

    private static final List<BlockAsset> ASSETS = List.of(
            new BlockAsset("assets/minecraft/textures/block/cobblestone.png", "frozen_cobblestone.png", Mode.COBBLESTONE),
            new BlockAsset("assets/minecraft/textures/block/stone_bricks.png", "frozen_stone_bricks.png", Mode.STONE_BRICKS),
            new BlockAsset("assets/minecraft/textures/block/oak_planks.png", "frozen_planks.png", Mode.PLANKS)
    );

    private GenerateColdBlockAssets() {
    }

    public static void main(String[] args) throws Exception {
        if (!Files.exists(INPUT_JAR)) {
            throw new IllegalStateException("Missing input resources jar: " + INPUT_JAR);
        }

        Files.createDirectories(OUTPUT_ROOT);
        try (ZipFile zip = new ZipFile(INPUT_JAR.toFile())) {
            for (BlockAsset asset : ASSETS) {
                BufferedImage source;
                try (InputStream stream = zip.getInputStream(zip.getEntry(asset.sourcePath))) {
                    source = ImageIO.read(stream);
                }

                if (source == null) {
                    throw new IllegalStateException("Unable to read source image: " + asset.sourcePath);
                }

                BufferedImage themed = recolor(source, asset.mode);
                addFrostRim(themed, asset.mode);
                addIceSpeckles(themed, asset.mode);
                accentDarkSeams(themed, asset.mode);

                Path output = OUTPUT_ROOT.resolve(asset.outputName);
                ImageIO.write(themed, "png", output.toFile());
            }
        }
    }

    private static BufferedImage recolor(BufferedImage source, Mode mode) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < source.getHeight(); y++) {
            float vertical = source.getHeight() <= 1 ? 0.0f : (float) y / (source.getHeight() - 1);
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }

                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                float luma = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255.0f;

                int[] palette = switch (mode) {
                    case COBBLESTONE -> gradient((float) Math.pow(luma, 0.88), 0x0A1018, 0x4A708D, 0xD8F4FF);
                    case STONE_BRICKS -> gradient((float) Math.pow(luma, 0.90), 0x0B1320, 0x587D98, 0xE4FAFF);
                    case PLANKS -> gradient((float) Math.pow(luma, 0.92), 0x0E1621, 0x40667D, 0xCCEAF7);
                };

                float frost = switch (mode) {
                    case COBBLESTONE -> vertical < 0.18f ? 0.12f : 0.0f;
                    case STONE_BRICKS -> vertical < 0.15f ? 0.10f : 0.0f;
                    case PLANKS -> vertical < 0.22f ? 0.08f : 0.0f;
                };

                int outRed = mix(palette[0], 238, frost);
                int outGreen = mix(palette[1], 247, frost);
                int outBlue = mix(palette[2], 255, frost);
                target.setRGB(x, y, alpha << 24 | outRed << 16 | outGreen << 8 | outBlue);
            }
        }

        return target;
    }

    private static void addFrostRim(BufferedImage image, Mode mode) {
        for (int x = 0; x < image.getWidth(); x++) {
            int depth = switch (mode) {
                case COBBLESTONE -> 2 + (x % 3 == 0 ? 1 : 0);
                case STONE_BRICKS -> 2;
                case PLANKS -> 1 + (x % 5 == 0 ? 1 : 0);
            };

            for (int y = 0; y < depth && y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                float blend = 0.36f - (y * 0.08f);
                if (blend > 0.0f) {
                    blendPixel(image, x, y, 240, 249, 255, blend);
                }
            }
        }
    }

    private static void addIceSpeckles(BufferedImage image, Mode mode) {
        int threshold = switch (mode) {
            case COBBLESTONE -> 7;
            case STONE_BRICKS -> 8;
            case PLANKS -> 10;
        };

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }

                int hash = (x * 734287 + y * 912271 + mode.ordinal() * 9719) & 15;
                if (hash > threshold) {
                    continue;
                }

                float blend = mode == Mode.PLANKS ? 0.10f : 0.16f;
                blendPixel(image, x, y, 222, 245, 255, blend);
            }
        }
    }

    private static void accentDarkSeams(BufferedImage image, Mode mode) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }

                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                float luma = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255.0f;

                if (luma > (mode == Mode.PLANKS ? 0.32f : 0.28f)) {
                    continue;
                }

                float blend = mode == Mode.PLANKS ? 0.18f : 0.26f;
                blendPixel(image, x, y, 165, 220, 244, blend);
            }
        }
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

    private static void blendPixel(BufferedImage image, int x, int y, int red, int green, int blue, float amount) {
        int argb = image.getRGB(x, y);
        int alpha = argb >>> 24;
        if (alpha == 0) {
            return;
        }

        int currentRed = (argb >>> 16) & 0xFF;
        int currentGreen = (argb >>> 8) & 0xFF;
        int currentBlue = argb & 0xFF;
        int outRed = mix(currentRed, red, amount);
        int outGreen = mix(currentGreen, green, amount);
        int outBlue = mix(currentBlue, blue, amount);
        image.setRGB(x, y, alpha << 24 | outRed << 16 | outGreen << 8 | outBlue);
    }

    private static int mix(int from, int to, float amount) {
        return from + Math.round((to - from) * amount);
    }

    private record BlockAsset(String sourcePath, String outputName, Mode mode) {
    }

    private enum Mode {
        COBBLESTONE,
        STONE_BRICKS,
        PLANKS
    }
}
