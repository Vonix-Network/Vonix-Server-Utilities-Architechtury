package network.vonix.serverutilities.api;

import network.vonix.serverutilities.VonixServerUtilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Registry + lookup for {@link InventoryProvider} implementations.
 *
 * <p>Two registration paths:
 * <ol>
 *   <li><b>Explicit:</b> call {@link #register(InventoryProvider)} during your mod's
 *       common-setup / FMLCommonSetupEvent handler.</li>
 *   <li><b>ServiceLoader:</b> ship a file at
 *       {@code META-INF/services/network.vonix.serverutilities.api.InventoryProvider}
 *       containing the FQN of your implementation. Scanned lazily on first
 *       {@link #providers()} call.</li>
 * </ol>
 *
 * <p>De-duplication: {@link #register} is last-write-wins by {@link InventoryProvider#id()},
 * so explicit registration of {@code "vonix:legacy_nbt"} (or any built-in id) will replace
 * the built-in. This is intentional — lets downstream override.
 *
 * <p>Order: providers are sorted ascending by {@link InventoryProvider#priority()}, so lower
 * runs first.
 *
 * <p>Part of the {@code network.vonix.serverutilities.api} published SPI — SemVer stable.
 */
public final class InventoryProviderRegistry {

    private static final List<InventoryProvider> PROVIDERS = new ArrayList<>();
    private static volatile boolean serviceLoaderScanned = false;

    private InventoryProviderRegistry() {}

    /** Register a provider. Last-write-wins by {@link InventoryProvider#id()}. */
    public static synchronized void register(InventoryProvider p) {
        if (p == null) return;
        PROVIDERS.removeIf(existing -> existing.id().equals(p.id()));
        PROVIDERS.add(p);
        PROVIDERS.sort(Comparator.comparingInt(InventoryProvider::priority));
    }

    /** Unmodifiable, priority-sorted view of all registered providers. Triggers a lazy ServiceLoader scan on first call. */
    public static synchronized List<InventoryProvider> providers() {
        if (!serviceLoaderScanned) loadServiceProviders();
        return Collections.unmodifiableList(new ArrayList<>(PROVIDERS));
    }

    private static void loadServiceProviders() {
        serviceLoaderScanned = true; // set first so re-entrant calls during scan don't recurse
        try {
            for (InventoryProvider p : ServiceLoader.load(InventoryProvider.class,
                    InventoryProviderRegistry.class.getClassLoader())) {
                register(p);
                VonixServerUtilities.LOGGER.info(
                        "[VonixSU/SPI] Loaded InventoryProvider via ServiceLoader: id={} priority={}",
                        p.id(), p.priority());
            }
        } catch (Throwable t) {
            VonixServerUtilities.LOGGER.warn("[VonixSU/SPI] ServiceLoader scan failed: {}", t.toString());
        }
    }
}
