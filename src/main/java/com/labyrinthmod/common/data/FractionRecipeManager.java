package com.labyrinthmod.common.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FractionRecipeManager {
    private static final Path FILE = Path.of("labyrinthmod", "fraction_recipes.json");
    private static final Map<ResourceLocation, Set<String>> ALLOWED = new HashMap<>();

    private FractionRecipeManager() {}

    public static void load() {
        ALLOWED.clear();
        installDefaults();
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            Type type = new TypeToken<Map<String, Set<String>>>() {}.getType();
            Map<String, Set<String>> entries = new Gson().fromJson(reader, type);
            if (entries == null) return;
            entries.forEach((id, factions) -> {
                ResourceLocation recipe = ResourceLocation.tryParse(id);
                if (recipe != null && factions != null) ALLOWED.put(recipe, factions.stream()
                        .map(s -> s.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()));
            });
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(FractionRecipeManager.class).error("Cannot load {}", FILE, ex);
        }
    }

    private static void installDefaults() {
        if (Files.exists(FILE)) return;
        try {
            Files.createDirectories(FILE.getParent());
            try (InputStream defaults = FractionRecipeManager.class.getResourceAsStream(
                    "/defaults/labyrinthmod/fraction_recipes.json")) {
                if (defaults == null) throw new IOException("Missing default fraction recipes");
                Files.copy(defaults, FILE);
            }
        } catch (IOException ex) {
            org.slf4j.LoggerFactory.getLogger(FractionRecipeManager.class)
                    .error("Cannot install default fraction recipes", ex);
        }
    }

    public static boolean canUse(ResourceLocation recipe, String fraction) {
        if (recipe == null) return true;
        return ALLOWED.getOrDefault(recipe, Collections.emptySet()).isEmpty()
                || ALLOWED.get(recipe).contains(fraction.toUpperCase(Locale.ROOT));
    }
}
