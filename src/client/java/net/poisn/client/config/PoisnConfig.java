package net.poisn.client.config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
public final class PoisnConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("poisn.json");
    public static Data data = new Data();
    private PoisnConfig() {}
    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    Data loaded = GSON.fromJson(reader, Data.class);
                    if (loaded != null) data = loaded;
                }
            } else save();
        } catch (Exception e) { System.err.println("[POISN] Could not load config: " + e.getMessage()); }
    }
    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) { GSON.toJson(data, writer); }
        } catch (Exception e) { System.err.println("[POISN] Could not save config: " + e.getMessage()); }
    }
    public static final class Data {
        public String marketApi = "https://269153435.de/api/mod/market/prices";
        public int marketRefreshSeconds = 60;
        public Map<String, PlayerTag> players = new LinkedHashMap<>();
    }
    public static final class PlayerTag {
        public String clan = "";
        public String aboveName = "";
        public String aboveColor = "&f";
        public String belowName = "";
        public String belowColor = "&7";
    }
}
