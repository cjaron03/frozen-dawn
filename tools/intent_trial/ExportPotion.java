import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the table she reads words with: potion-base-8M at int8, a scale per row, as IntentTrial's Potion.at(8) shapes it.
 * Run from the repository root, with the model in build/intent-trial/potion (docs/aurora-intent-trial.md):
 * java -cp <gson.jar> tools/intent_trial/ExportPotion.java build/intent-trial/potion src/main/resources/frozendawn/potion
 */
public final class ExportPotion {
    public static void main(String[] args) throws IOException {
        Path model = Path.of(args[0]), out = Path.of(args[1]);
        byte[] file = Files.readAllBytes(model.resolve("model.safetensors"));
        var buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        int header = (int) buffer.getLong();
        var shape = new Gson().fromJson(new String(file, 8, header, StandardCharsets.UTF_8), JsonObject.class).getAsJsonObject("embeddings").getAsJsonArray("shape");
        int rows = shape.get(0).getAsInt(), width = shape.get(1).getAsInt();
        buffer.position(8 + header);
        try (var table = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out.resolve("table.bin"))))) {
            table.writeInt(rows);
            table.writeInt(width);
            var row = new float[width];
            for (int r = 0; r < rows; r++) {
                float max = 0;
                for (int d = 0; d < width; d++) {
                    row[d] = buffer.getFloat();
                    max = Math.max(max, Math.abs(row[d]));
                }
                float scale = max == 0 ? 1 : max / 127;
                table.writeFloat(scale);
                for (float x : row) table.writeByte(Math.round(x / scale));
            }
        }
        Files.copy(model.resolve("vocab.txt"), out.resolve("vocab.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("%d rows of %d%n", rows, width);
    }
}
