package net.poisn.client.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.poisn.client.config.PoisnConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MarketPriceService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "POISN-Market-Refresh");
                thread.setDaemon(true);
                return thread;
            });

    private static volatile Map<String, Prices> cache = Map.of();
    private static volatile String lastUpdateDay = "";
    private static boolean started;

    private MarketPriceService() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        long seconds = Math.max(15, PoisnConfig.data.marketRefreshSeconds);
        // Run immediately, but never block the Minecraft client thread.
        EXECUTOR.scheduleAtFixedRate(
                MarketPriceService::refresh, 0, seconds, TimeUnit.SECONDS);
    }

    public static Prices get(String itemId) {
        if (itemId == null) {
            return null;
        }
        return cache.get(itemId.toLowerCase(Locale.ROOT));
    }

    public static String getLastUpdateDay() {
        return lastUpdateDay;
    }

    private static void refresh() {
        try {
            URI uri = URI.create(PoisnConfig.data.marketApi);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }

            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").isJsonPrimitive()
                    || !root.get("ok").getAsBoolean()
                    || !root.has("items") || !root.get("items").isJsonObject()) {
                return;
            }

            JsonObject items = root.getAsJsonObject("items");
            Map<String, Prices> fresh = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                JsonElement element = entry.getValue();
                if (!element.isJsonArray()) {
                    continue;
                }
                JsonArray values = element.getAsJsonArray();
                if (values.size() < 2
                        || !values.get(0).isJsonPrimitive()
                        || !values.get(1).isJsonPrimitive()
                        || !values.get(0).getAsJsonPrimitive().isNumber()
                        || !values.get(1).getAsJsonPrimitive().isNumber()) {
                    continue;
                }

                double ahPrice = values.get(0).getAsDouble();
                double orderPrice = values.get(1).getAsDouble();
                if (!Double.isFinite(ahPrice) || !Double.isFinite(orderPrice)
                        || ahPrice < 0 || orderPrice < 0) {
                    continue;
                }
                fresh.put(entry.getKey().toLowerCase(Locale.ROOT),
                        new Prices(ahPrice, orderPrice));
            }

            // Keep the previous successful snapshot if the response is empty/invalid.
            if (!fresh.isEmpty()) {
                cache = Map.copyOf(fresh);
                lastUpdateDay = root.has("day") && root.get("day").isJsonPrimitive()
                        ? root.get("day").getAsString() : "";
            }
        } catch (Exception exception) {
            System.err.println("[POISN] Market refresh failed; keeping last successful prices: "
                    + exception.getMessage());
        }
    }

    public record Prices(double ahPrice, double orderPrice) {
    }
}
