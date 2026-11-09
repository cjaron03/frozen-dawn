package com.frozendawn.lore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Patchouli's search matches an entry's title or the names of its relevant
 * stacks, never page text. These checks follow the 1.21.1-92 rules for what
 * becomes a relevant stack.
 */
class ManualSearchTest {
    private static final Path RESOURCES = resourceRoot();
    private static final Path ENTRIES = RESOURCES.resolve(
            "assets/frozendawn/patchouli_books/frozen_dawn_guide/en_us/entries");
    private static final Path RECIPES = RESOURCES.resolve("data");

    /** Plan Rulings 1 and 2: entry id to the items it must be found by. */
    private static final Map<String, List<String>> ADDED = Map.ofEntries(
            Map.entry("advanced/rocket_assembly", List.of("rocket_engine", "rocket_hull")),
            Map.entry("fabricator/overview", List.of("fabricator")),
            Map.entry("fabricator/slates", List.of("data_slate")),
            Map.entry("getting_started/ice_fishing",
                    List.of("glassblood", "rime_hagfish", "longsleeper")),
            Map.entry("getting_started/seafood_boil", List.of("seafood_boil_bowl")),
            Map.entry("orsa_equipment/comfort_items", List.of("miel_honey")),
            Map.entry("icarus/overview", List.of("icarus_controller")),
            Map.entry("icarus/fuel", List.of("antimatter_capsule", "empty_antimatter_capsule")),
            Map.entry("sfr/overview", List.of("sfr_controller")),
            Map.entry("icarus/getting_it", List.of(
                    "icarus_frame", "icarus_matter_port", "icarus_power_port",
                    "icarus_capsule_dock", "icarus_reserve_bank", "icarus_injector_casing",
                    "icarus_stabilizer_housing", "halsey_core", "icarus_stabilizer",
                    "icarus_injector", "antiparticle_foundry")),
            Map.entry("sfr/building", List.of(
                    "magnet_coil", "confinement_core", "first_wall", "lithium_blanket",
                    "sfr_fuel_port", "sfr_power_port", "sfr_vacuum_port",
                    "sfr_maintenance_hatch", "high_temperature_radiator")),
            Map.entry("sfr/fuel", List.of(
                    "deuterium_canister", "isotope_column", "lithium_target_rod",
                    "raw_spodumene", "deepslate_spodumene_ore", "pegmatite",
                    "lithium_carbonate", "lithium", "helium_canister")),
            Map.entry("reactor/meltdown", List.of("molten_corium")),
            Map.entry("orsa_intel/field_documents", List.of("orsa_document")));

    @Test
    void everyListedItemIsSearchable() {
        Map<String, Set<String>> book = searchableByEntry();
        List<String> missing = new ArrayList<>();
        ADDED.forEach((entry, items) -> {
            Set<String> found = book.getOrDefault(entry, Set.of());
            for (String item : items) {
                if (!found.contains("frozendawn:" + item)) {
                    missing.add(entry + " -> " + item);
                }
            }
        });
        assertTrue(missing.isEmpty(), "Not searchable: " + missing);
    }

    @Test
    void mappingsResolve() {
        JsonObject lang = PayoffCluesTest.lang();
        List<String> broken = new ArrayList<>();
        entries().forEach((id, entry) -> {
            if (!entry.has("extra_recipe_mappings")) {
                return;
            }
            int pages = entry.getAsJsonArray("pages").size();
            for (var m : entry.getAsJsonObject("extra_recipe_mappings").entrySet()) {
                String key = m.getKey();
                int page = m.getValue().getAsInt();
                if (!key.startsWith("minecraft:") && !named(lang, key)) {
                    broken.add(id + ": no item " + key);
                }
                if (page < 0 || page >= pages) {
                    broken.add(id + ": " + key + " page " + page + " of " + pages);
                }
            }
        });
        assertTrue(broken.isEmpty(), "Broken mappings: " + broken);
    }

    @Test
    void noItemHasTwoHomes() {
        Map<String, Set<String>> book = searchableByEntry();
        List<String> doubled = new ArrayList<>();
        ADDED.values().stream().flatMap(List::stream).distinct().forEach(item -> {
            List<String> homes = new ArrayList<>();
            book.forEach((entry, items) -> {
                if (items.contains("frozendawn:" + item)) {
                    homes.add(entry);
                }
            });
            if (homes.size() > 1) {
                doubled.add(item + " in " + homes);
            }
        });
        assertTrue(doubled.isEmpty(), "Items with two homes: " + doubled);
    }

    @Test
    void startHereMentionsSearch() {
        String page0 = entries().get("getting_started/start_here")
                .getAsJsonArray("pages").get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(page0.endsWith(
                        "$(br2)Search an item's name on the front page to find every entry about it."),
                page0);
    }

    private static boolean named(JsonObject lang, String id) {
        String[] parts = id.split(":", 2);
        return lang.has("item." + parts[0] + "." + parts[1])
                || lang.has("block." + parts[0] + "." + parts[1]);
    }

    /** Relevant stacks per entry, by the rules in the plan's "How search works". */
    static Map<String, Set<String>> searchableByEntry() {
        Map<String, Set<String>> out = new TreeMap<>();
        entries().forEach((id, entry) -> {
            Set<String> items = new LinkedHashSet<>();
            for (JsonElement el : entry.getAsJsonArray("pages")) {
                JsonObject page = el.getAsJsonObject();
                String type = page.get("type").getAsString();
                boolean link = page.has("link_recipe")
                        ? page.get("link_recipe").getAsBoolean()
                        : !type.equals("patchouli:spotlight");
                if (!link) {
                    continue;
                }
                if (type.equals("patchouli:spotlight")) {
                    items.add(stripNbt(page.get("item").getAsString()));
                } else if (type.equals("patchouli:crafting") || type.equals("patchouli:smelting")) {
                    for (String key : List.of("recipe", "recipe2")) {
                        if (page.has(key)) {
                            items.add(recipeResult(page.get(key).getAsString()));
                        }
                    }
                }
            }
            if (entry.has("extra_recipe_mappings")) {
                items.addAll(entry.getAsJsonObject("extra_recipe_mappings").keySet());
            }
            out.put(id, items);
        });
        return out;
    }

    private static String stripNbt(String stack) {
        int cut = stack.indexOf('[');
        String id = cut < 0 ? stack : stack.substring(0, cut);
        int count = id.indexOf('#');
        return count < 0 ? id : id.substring(0, count);
    }

    private static String recipeResult(String recipeId) {
        String[] parts = recipeId.split(":", 2);
        Path file = RECIPES.resolve(parts[0]).resolve("recipe").resolve(parts[1] + ".json");
        if (!Files.exists(file)) {
            // Vanilla recipe ids name their result.
            return recipeId;
        }
        JsonObject recipe = read(file);
        JsonElement result = recipe.get("result");
        return result.isJsonObject()
                ? result.getAsJsonObject().get("id").getAsString()
                : result.getAsString();
    }

    static Map<String, JsonObject> entries() {
        Map<String, JsonObject> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(ENTRIES)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String id = ENTRIES.relativize(p).toString().replace('\\', '/');
                out.put(id.substring(0, id.length() - ".json".length()), read(p));
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** The main resources root on the test classpath. */
    private static Path resourceRoot() {
        String probe = "assets/frozendawn/lang/en_us.json";
        try {
            var url = ManualSearchTest.class.getClassLoader().getResource(probe);
            // Four levels up from the lang file, on whatever file system holds it.
            return Path.of(url.toURI()).getParent().getParent().getParent().getParent();
        } catch (Exception e) {
            throw new IllegalStateException(probe, e);
        }
    }

    private static JsonObject read(Path file) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(file.toString(), e);
        }
    }
}
