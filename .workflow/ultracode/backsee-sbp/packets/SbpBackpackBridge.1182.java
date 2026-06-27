package network.vonix.serverutilities.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import network.vonix.serverutilities.VonixServerUtilities;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Soft-dep reflection bridge to <b>Sophisticated Backpacks</b>'s
 * {@code BackpackStorage} world-save data.
 *
 * <p>SBP is an All-Rights-Reserved mod, so VSU must not link against it at
 * compile time and must not redistribute SBP source/bytecode. This bridge
 * uses reflection only — when SBP isn't on the classpath, every public
 * method short-circuits and the caller behaves as if SBP were absent.
 *
 * <h3>Data layout (SBP 3.20.x on MC 1.18.2 — same shape on 1.19/1.20)</h3>
 * Each worn/inventory backpack item carries an NBT tag {@code contentsUuid}
 * (16-byte int array, vanilla UUID encoding). The actual inventory is stored
 * externally in {@code BackpackStorage}'s {@code Map<UUID, CompoundTag>}:
 * <pre>
 *   storage.backpackContents.get(uuid) = {
 *     "inventory": {
 *       "Items": ListTag&lt;CompoundTag&gt;[ {Slot: byte, id: ..., Count: ..., tag: ...}, ... ],
 *       "Size":  IntTag
 *     },
 *     "upgrades":      { Items: ListTag, Size: int },  // not exposed by this bridge
 *     "settings":      { ... },                        // not exposed
 *     ...
 *   }
 * </pre>
 *
 * <p>This bridge intentionally exposes ONLY the {@code inventory.Items} list —
 * not upgrades or settings. {@code /backsee} is a viewer/editor of items, not
 * a full backpack-configuration tool.
 *
 * <h3>Thread safety</h3>
 * All methods must be called on the server thread (capability/world-data
 * access). Caller is responsible.
 */
public final class SbpBackpackBridge {

    private static final String STORAGE_CLASS = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage";
    private static final String CONTENTS_UUID_TAG = "contentsUuid";
    private static final String INVENTORY_SUBTAG = "inventory";
    public  static final String ITEMS_LIST_KEY = "Items";

    // Resolved at first call to isAvailable(); never re-resolved.
    private static volatile Boolean availableCache;
    private static volatile Method  storageGetMethod;        // BackpackStorage.get() -> BackpackStorage
    private static volatile Method  getOrCreateContentsMethod; // .getOrCreateBackpackContents(UUID) -> CompoundTag
    private static volatile Method  setDirtyMethod;          // SavedData#setDirty()

    private static final AtomicBoolean warnedAbsent = new AtomicBoolean(false);

    private SbpBackpackBridge() {}

    /**
     * @return true iff SBP's {@code BackpackStorage} class can be resolved and
     *         the expected static {@code get()} method is present. Result is
     *         cached after the first call.
     */
    public static boolean isAvailable() {
        Boolean cached = availableCache;
        if (cached != null) return cached;
        synchronized (SbpBackpackBridge.class) {
            if (availableCache != null) return availableCache;
            try {
                Class<?> storage = Class.forName(STORAGE_CLASS, true, SbpBackpackBridge.class.getClassLoader());
                storageGetMethod = storage.getMethod("get");
                getOrCreateContentsMethod = storage.getMethod("getOrCreateBackpackContents", UUID.class);
                // setDirty() is on SavedData (parent class) — public, inherited.
                setDirtyMethod = storage.getMethod("setDirty");
                availableCache = Boolean.TRUE;
                VonixServerUtilities.LOGGER.info(
                        "[VonixSU] SophisticatedBackpacks detected — /backsee SBP path enabled.");
            } catch (ClassNotFoundException e) {
                availableCache = Boolean.FALSE;
                // Don't log anything — SBP is genuinely not installed, that's fine.
            } catch (NoSuchMethodException e) {
                availableCache = Boolean.FALSE;
                VonixServerUtilities.LOGGER.warn(
                        "[VonixSU] SBP found but method shape changed — disabling /backsee SBP path: {}",
                        e.getMessage());
            }
            return availableCache;
        }
    }

    /**
     * Reads the {@code contentsUuid} NBT tag from an item stack. No reflection
     * needed — this is plain vanilla NBT.
     *
     * @return present if the stack carries a valid {@code contentsUuid} tag
     */
    public static Optional<UUID> readContentsUuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(CONTENTS_UUID_TAG)) return Optional.empty();
        try {
            return Optional.of(tag.getUUID(CONTENTS_UUID_TAG));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Reflectively fetches the SBP-side root {@link CompoundTag} for the given
     * backpack UUID. The returned tag is the same instance SBP holds in its
     * {@code Map<UUID, CompoundTag>}, so in-place mutations are persisted on
     * the next world save (after calling {@link #markDirty()}).
     *
     * @return the root contents compound, or empty if SBP isn't available /
     *         the lookup throws.
     */
    public static Optional<CompoundTag> getContentsRoot(UUID uuid) {
        if (!isAvailable() || uuid == null) return Optional.empty();
        try {
            Object storage = storageGetMethod.invoke(null);
            if (storage == null) return Optional.empty();
            Object result = getOrCreateContentsMethod.invoke(storage, uuid);
            if (result instanceof CompoundTag ct) return Optional.of(ct);
            return Optional.empty();
        } catch (Throwable t) {
            if (warnedAbsent.compareAndSet(false, true)) {
                VonixServerUtilities.LOGGER.warn(
                        "[VonixSU] SBP bridge failed reflective call (will not warn again this run): {}",
                        t.toString());
            }
            return Optional.empty();
        }
    }

    /**
     * Convenience: returns the inventory-Items {@link ListTag} from a backpack's
     * root contents tag. May be an empty list (newly-created storage entry) but
     * is never null when the optional is present.
     */
    public static Optional<ListTag> getItemsList(CompoundTag contentsRoot) {
        if (contentsRoot == null) return Optional.empty();
        if (!contentsRoot.contains(INVENTORY_SUBTAG, Tag.TAG_COMPOUND)) {
            // Fresh entry: create the substructure so writes have a home.
            CompoundTag inv = new CompoundTag();
            inv.put(ITEMS_LIST_KEY, new ListTag());
            contentsRoot.put(INVENTORY_SUBTAG, inv);
        }
        CompoundTag inv = contentsRoot.getCompound(INVENTORY_SUBTAG);
        if (!inv.contains(ITEMS_LIST_KEY, Tag.TAG_LIST)) {
            inv.put(ITEMS_LIST_KEY, new ListTag());
        }
        return Optional.of(inv.getList(ITEMS_LIST_KEY, Tag.TAG_COMPOUND));
    }

    /**
     * Replaces the inventory-Items list inside the root contents compound, in
     * place. Caller must then {@link #markDirty()} for the change to be saved.
     */
    public static void setItemsList(CompoundTag contentsRoot, ListTag newItems) {
        if (contentsRoot == null || newItems == null) return;
        CompoundTag inv = contentsRoot.contains(INVENTORY_SUBTAG, Tag.TAG_COMPOUND)
                ? contentsRoot.getCompound(INVENTORY_SUBTAG)
                : new CompoundTag();
        inv.put(ITEMS_LIST_KEY, newItems);
        contentsRoot.put(INVENTORY_SUBTAG, inv);
    }

    /**
     * Marks SBP's {@code BackpackStorage} dirty so the next world save flushes
     * the in-memory map to disk.
     */
    public static void markDirty() {
        if (!isAvailable()) return;
        try {
            Object storage = storageGetMethod.invoke(null);
            if (storage != null) setDirtyMethod.invoke(storage);
        } catch (Throwable t) {
            if (warnedAbsent.compareAndSet(false, true)) {
                VonixServerUtilities.LOGGER.warn(
                        "[VonixSU] SBP bridge markDirty failed: {}", t.toString());
            }
        }
    }
}
