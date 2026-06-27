package network.vonix.serverutilities.inventory;

import net.minecraft.world.item.ItemStack;
import network.vonix.serverutilities.VonixServerUtilities;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Universal Forge / NeoForge {@code IItemHandler} capability walk used by
 * {@code /backsee}. All access is reflective so {@code common/} stays
 * platform-agnostic and the same source file compiles on every template.
 *
 * <p>Probes (first non-null wins):
 * <ol>
 *   <li>NeoForge — {@code net.neoforged.neoforge.capabilities.Capabilities$ItemHandler#ITEM}
 *       ({@code ItemCapability<IItemHandler, Void>});
 *       {@code ItemStack#getCapability(ItemCapability, Object)} returns the handler directly.</li>
 *   <li>Forge 1.19.3+ — {@code net.minecraftforge.common.capabilities.ForgeCapabilities#ITEM_HANDLER}
 *       ({@code Capability<IItemHandler>});
 *       {@code ItemStack#getCapability(Capability)} returns {@code LazyOptional<IItemHandler>}.</li>
 *   <li>Forge 1.18.2 / 1.19.2 — {@code net.minecraftforge.items.CapabilityItemHandler#ITEM_HANDLER_CAPABILITY}
 *       (same {@code Capability<IItemHandler>} shape).</li>
 * </ol>
 *
 * <p>This handles every well-behaved capability-exposing item: Sophisticated
 * Backpacks/Storage shulker, vanilla shulker (via Forge default cap provider),
 * Iron Chests shulker, Traveler's Backpack, Iron Backpacks, FunctionalStorage
 * drawers-as-item, etc.
 *
 * <p>Fail-closed: every reflective call is wrapped; on {@link Throwable} the
 * probe returns {@link Optional#empty()} and the caller falls through to the
 * legacy NBT walk. A single WARN is logged the first time a reflective
 * invocation throws.
 *
 * <p>Thread safety: caller (command handler) is on the server thread.
 */
public final class CapabilityInventoryBridge {

    private static final AtomicBoolean warnedAbsent = new AtomicBoolean(false);

    // Resolved at first call; cached forever.
    private static volatile Boolean availableCache;
    private static volatile Object  capObject;           // Capability<IItemHandler> | ItemCapability<IItemHandler,Void>
    private static volatile Method  stackGetCapability;  // ItemStack#getCapability(...)
    private static volatile boolean stackGetCapabilityTakesContext; // NeoForge 2-arg variant
    private static volatile boolean returnsLazyOptional; // Forge: needs .resolve() unwrap
    private static volatile Method  lazyOptionalResolve; // LazyOptional#resolve() -> Optional
    private static volatile Method  handlerGetSlots;
    private static volatile Method  handlerGetStackInSlot;
    private static volatile Method  handlerSetStackInSlot; // IItemHandlerModifiable; may be null

    private CapabilityInventoryBridge() {}

    public static boolean isAvailable() {
        Boolean cached = availableCache;
        if (cached != null) return cached;
        synchronized (CapabilityInventoryBridge.class) {
            if (availableCache != null) return availableCache;
            availableCache = probe();
            return availableCache;
        }
    }

    private static boolean probe() {
        // --- Resolve capability constant. ---
        Object cap = null;
        boolean neo = false;
        try {
            Class<?> neoCaps = Class.forName(
                    "net.neoforged.neoforge.capabilities.Capabilities$ItemHandler");
            cap = neoCaps.getField("ITEM").get(null);
            neo = true;
        } catch (Throwable ignore) {}
        if (cap == null) {
            try {
                Class<?> fc = Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities");
                cap = fc.getField("ITEM_HANDLER").get(null);
            } catch (Throwable ignore) {}
        }
        if (cap == null) {
            try {
                Class<?> cih = Class.forName("net.minecraftforge.items.CapabilityItemHandler");
                Field f = cih.getField("ITEM_HANDLER_CAPABILITY");
                cap = f.get(null);
            } catch (Throwable ignore) {}
        }
        if (cap == null) return Boolean.FALSE; // Fabric or stripped runtime — silent.

        // --- Resolve IItemHandler interface (NeoForge or Forge package). ---
        Class<?> handlerIface = null;
        try { handlerIface = Class.forName("net.neoforged.neoforge.items.IItemHandler"); } catch (Throwable ignore) {}
        if (handlerIface == null) {
            try { handlerIface = Class.forName("net.minecraftforge.items.IItemHandler"); } catch (Throwable ignore) {}
        }
        if (handlerIface == null) return Boolean.FALSE;

        // --- Resolve IItemHandlerModifiable (optional — for setStackInSlot). ---
        Class<?> modifiableIface = null;
        try { modifiableIface = Class.forName("net.neoforged.neoforge.items.IItemHandlerModifiable"); } catch (Throwable ignore) {}
        if (modifiableIface == null) {
            try { modifiableIface = Class.forName("net.minecraftforge.items.IItemHandlerModifiable"); } catch (Throwable ignore) {}
        }

        // --- Find ItemStack#getCapability matching this cap. ---
        Method chosen = null;
        boolean takesContext = false;
        for (Method m : ItemStack.class.getMethods()) {
            if (!m.getName().equals("getCapability")) continue;
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length == 0) continue;
            if (!ps[0].isInstance(cap)) continue;
            // Prefer 1-arg (Forge) if present; otherwise 2-arg (NeoForge ItemCapability + context).
            if (ps.length == 1) { chosen = m; takesContext = false; break; }
            if (ps.length == 2 && chosen == null) { chosen = m; takesContext = true; }
        }
        if (chosen == null) return Boolean.FALSE;

        // --- Determine return-type unwrap path. ---
        boolean lazy = chosen.getReturnType().getName().endsWith(".LazyOptional");
        Method resolveM = null;
        if (lazy) {
            try {
                resolveM = chosen.getReturnType().getMethod("resolve");
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        }

        // --- IItemHandler method handles. ---
        Method getSlots, getStackInSlot;
        try {
            getSlots = handlerIface.getMethod("getSlots");
            getStackInSlot = handlerIface.getMethod("getStackInSlot", int.class);
        } catch (Throwable t) {
            return Boolean.FALSE;
        }
        Method setStackInSlot = null;
        if (modifiableIface != null) {
            try {
                setStackInSlot = modifiableIface.getMethod("setStackInSlot", int.class, ItemStack.class);
            } catch (Throwable ignore) {}
        }

        capObject = cap;
        stackGetCapability = chosen;
        stackGetCapabilityTakesContext = takesContext;
        returnsLazyOptional = lazy;
        lazyOptionalResolve = resolveM;
        handlerGetSlots = getSlots;
        handlerGetStackInSlot = getStackInSlot;
        handlerSetStackInSlot = setStackInSlot;

        VonixServerUtilities.LOGGER.info(
                "[VonixSU] IItemHandler capability probe OK ({}). /backsee capability walk enabled.",
                neo ? "NeoForge" : "Forge");
        return Boolean.TRUE;
    }

    /**
     * Resolve the {@code IItemHandler} exposed by {@code stack} (if any) and
     * wrap it in a VSU-side adapter. Fail-closed.
     */
    public static Optional<Handler> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        if (!isAvailable()) return Optional.empty();
        try {
            Object raw;
            if (stackGetCapabilityTakesContext) {
                raw = stackGetCapability.invoke(stack, capObject, null);
            } else {
                raw = stackGetCapability.invoke(stack, capObject);
            }
            if (raw == null) return Optional.empty();

            Object handler;
            if (returnsLazyOptional) {
                Object opt = lazyOptionalResolve.invoke(raw);
                if (!(opt instanceof Optional<?> o) || o.isEmpty()) return Optional.empty();
                handler = o.get();
            } else {
                handler = raw;
            }
            if (handler == null) return Optional.empty();

            int slots;
            try {
                slots = (int) handlerGetSlots.invoke(handler);
            } catch (Throwable t) {
                warnOnce("getSlots", t);
                return Optional.empty();
            }
            if (slots <= 0) return Optional.empty();
            return Optional.of(new Handler(handler, slots));
        } catch (Throwable t) {
            warnOnce("getCapability", t);
            return Optional.empty();
        }
    }

    private static void warnOnce(String where, Throwable t) {
        if (warnedAbsent.compareAndSet(false, true)) {
            VonixServerUtilities.LOGGER.warn(
                    "[VonixSU] Capability bridge reflective call failed at {} (will not warn again this run): {}",
                    where, t.toString());
        }
    }

    /**
     * VSU-side handle to a resolved {@code IItemHandler}. No Forge / NeoForge
     * types leak through this surface.
     */
    public static final class Handler {
        private final Object handler;
        private final int slots;

        Handler(Object handler, int slots) {
            this.handler = handler;
            this.slots = slots;
        }

        public int getSlots() { return slots; }

        public ItemStack getStackInSlot(int slot) {
            try {
                Object r = handlerGetStackInSlot.invoke(handler, slot);
                return r instanceof ItemStack s ? s : ItemStack.EMPTY;
            } catch (Throwable t) {
                warnOnce("getStackInSlot", t);
                return ItemStack.EMPTY;
            }
        }

        /**
         * @return true if the handler exposed {@code IItemHandlerModifiable}
         *         and the call succeeded.
         */
        public boolean setStackInSlot(int slot, ItemStack stack) {
            if (handlerSetStackInSlot == null) return false;
            try {
                handlerSetStackInSlot.invoke(handler, slot, stack);
                return true;
            } catch (Throwable t) {
                warnOnce("setStackInSlot", t);
                return false;
            }
        }

        /**
         * @return true iff this handler is writable (implements
         *         {@code IItemHandlerModifiable}).
         */
        public boolean isModifiable() {
            if (handlerSetStackInSlot == null) return false;
            Class<?> decl = handlerSetStackInSlot.getDeclaringClass();
            return decl.isInstance(handler);
        }
    }
}
