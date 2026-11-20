package net.poisn.client;
import net.fabricmc.api.ClientModInitializer;
import net.poisn.client.config.PoisnConfig;
import net.poisn.client.market.MarketPriceService;
public final class PoisnClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        PoisnConfig.load();
        MarketPriceService.start();
    }
}
