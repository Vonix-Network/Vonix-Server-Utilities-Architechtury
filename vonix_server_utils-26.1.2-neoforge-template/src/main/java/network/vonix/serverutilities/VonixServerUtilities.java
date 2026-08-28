package network.vonix.serverutilities;

import network.vonix.serverutilities.api.InventoryProviderRegistry;
import network.vonix.serverutilities.database.Database;
import network.vonix.serverutilities.inventory.providers.CapabilityInventoryProvider;
import network.vonix.serverutilities.inventory.providers.CuriosInventoryProvider;
import network.vonix.serverutilities.inventory.providers.DataComponentsInventoryProvider;
import network.vonix.serverutilities.inventory.providers.LegacyNbtInventoryProvider;
import network.vonix.serverutilities.listener.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main singleton for Vonix Server Utilities.
 * Call {@link #init()} from both the Fabric and NeoForge entry points.
 */
public final class VonixServerUtilities {
    public static final String MOD_ID  = "vonix_server_utilities";
    public static final String VERSION = "2.0.0";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    private static VonixServerUtilities instance;
    private final Database database = new Database();

    /**
     * Single-threaded executor for all database I/O.
     * One thread serialises writes, avoids concurrent-access bugs on the
     * single SQLite connection, and keeps the main tick thread clear.
     */
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VonixSU-DB");
        t.setDaemon(true);
        return t;
    });

    private VonixServerUtilities() {}

    public static void init() {
        instance = new VonixServerUtilities();
        registerBuiltinInventoryProviders();
        EventHandler.init();
        LOGGER.info("[VonixSU] Initialized.");
    }

    /**
     * Register built-in {@link network.vonix.serverutilities.api.InventoryProvider}s used by
     * {@code /backsee}. 1.21+ also registers DataComponentsInventoryProvider for vanilla
     * shulker/bundle DataComponents.CONTAINER support.
     */
    private static void registerBuiltinInventoryProviders() {
        InventoryProviderRegistry.register(new CuriosInventoryProvider());
        InventoryProviderRegistry.register(new DataComponentsInventoryProvider());
        InventoryProviderRegistry.register(new CapabilityInventoryProvider());
        InventoryProviderRegistry.register(new LegacyNbtInventoryProvider());
        LOGGER.info("[VonixSU/SPI] Built-in InventoryProviders registered: curios, data_components, capability, legacy_nbt");
    }

    public static VonixServerUtilities getInstance() { return instance; }
    public Database getDatabase()                    { return database; }

    /**
     * Submit a task to the off-thread database executor.
     * ALL database reads and writes must go through here.
     * Use {@code server.execute(runnable)} inside the task to bounce results
     * back to the main tick thread for any Minecraft operations.
     */
    public static void dbAsync(Runnable task) {
        instance.dbExecutor.execute(task);
    }

    /** Gracefully shut down the DB executor. Called on server stop. */
    public void shutdown() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
