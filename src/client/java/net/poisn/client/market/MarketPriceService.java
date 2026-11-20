package net.poisn.client.market;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.poisn.client.config.PoisnConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
public final class MarketPriceService {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "POISN-Market-Refresh"); t.setDaemon(true); return t;
    });
    private static final Map<String, Prices> CACHE = new ConcurrentHashMap<>();
    private static volatile String lastUpdateDay = "";
    private MarketPriceService() {}
    public static void start() {
        long seconds = Math.max(15, PoisnConfig.data.marketRefreshSeconds);
        // Never perform blocking network I/O on Minecraft's client initialization thread.
        EXECUTOR.execute(MarketPriceService::refresh);
        EXECUTOR.scheduleAtFixedRate(MarketPriceService::refresh, seconds, seconds, TimeUnit.SECONDS);
    }
    public static Prices get(String itemId) { return itemId == null ? null : CACHE.get(itemId.toLowerCase()); }
    public static String getLastUpdateDay() { return lastUpdateDay; }
    private static void refresh() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(PoisnConfig.data.marketApi))
                .timeout(Duration.ofSeconds(12)).header("Accept", "application/json").GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").getAsBoolean() || !root.has("items")) return;
            JsonObject items = root.getAsJsonObject("items");
            Map<String, Prices> fresh = new ConcurrentHashMap<>();
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                if (!entry.getValue().isJsonArray() || entry.getValue().getAsJsonArray().size() < 2) continue;
                var values = entry.getValue().getAsJsonArray();
                if (values.get(0).isJsonNull() || values.get(1).isJsonNull()) continue;
                fresh.put(entry.getKey().toLowerCase(), new Prices(values.get(0).getAsDouble(), values.get(1).getAsDouble()));
            }
            if (!fresh.isEmpty()) {
                CACHE.putAll(fresh);
                lastUpdateDay = root.has("day") ? root.get("day").getAsString() : "";
            }
        } catch (Exception e) {
            System.err.println("[POISN] Market refresh failed; keeping last successful prices: " + e.getMessage());
        }
    }
    public record Prices(double ahPrice, double orderPrice) {}
}
