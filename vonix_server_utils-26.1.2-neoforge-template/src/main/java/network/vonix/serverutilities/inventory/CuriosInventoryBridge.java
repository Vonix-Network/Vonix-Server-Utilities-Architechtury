package network.vonix.serverutilities.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import network.vonix.serverutilities.VonixServerUtilities;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Soft-dep reflective bridge to the Curios API.
 *
 * <p>Curios is an OPTIONAL dependency: this whole class is reflection-only
 * and fails closed if {@code top.theillusivec4.curios.api.CuriosApi} is not
 * on the classpath. The {@code common/} sources never reference any Curios
 * type at compile time — the same source compiles on every template.
 *
 * <p>What it does: enumerate every item stack in every Curios slot on a
 * given player. Used by {@code /backsee} to find backpacks/pouches that
 * are stashed in cosmetic/charm/ring/belt/etc. slots instead of the main
 * inventory.
 *
 * <p>Probe path (Curios 1.18/1.19/1.20/1.21 — all the same FQN):
 * <pre>
 *   CuriosApi.getCuriosInventory(LivingEntity) -> LazyOptional&lt;ICuriosItemHandler&gt;
 *   ICuriosItemHandler.getCurios() -> Map&lt;String, ICurioStacksHandler&gt;
 *   ICurioStacksHandler.getStacks() -> IDynamicStackHandler (extends IItemHandlerModifiable)
 *   IItemHandler.getSlots() / getStackInSlot(int)
 * </pre>
 *
 * <p>Fail-closed everywhere: any {@link Throwable} during the probe or a
 * live walk silently disables the bridge and returns
 * {@link Optional#empty()}.
 */
public final class CuriosInventoryBridge {

    private static final AtomicBoolean warnedFault = new AtomicBoolean(false);

    private static volatile Boolean availableCache;
    private static volatile Method  curiosApiGetInventory;   // CuriosApi.getCuriosInventory(LivingEntity)
    private static volatile Method  lazyOptionalResolve;     // LazyOptional#resolve() -> Optional
    private static volatile boolean returnsLazyOptional;
    private static volatile Method  iCuriosItemHandlerGetCurios; // ICuriosItemHandler#getCurios() -> Map
    private static volatile Method  iCurioStacksHandlerGetStacks; // ICurioStacksHandler#getStacks() -> IItemHandler
    private static volatile Method  iItemHandlerGetSlots;
    private static volatile Method  iItemHandlerGetStackInSlot;

    private CuriosInventoryBridge() {}

    public static boolean isAvailable() {
        Boolean cached = availableCache;
        if (cached != null) return cached;
        synchronized (CuriosInventoryBridge.class) {
            if (availableCache != null) return availableCache;
            availableCache = probe();
            return availableCache;
        }
    }

    private static boolean probe() {
        try {
            Class<?> apiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            // Probe the method so unsupported Curios API releases fail closed.
            Method getInv = null;
            for (Method m : apiClass.getMethods()) {
                if (!m.getName().equals("getCuriosInventory")) continue;
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != 1) continue;
                if (!ps[0].getName().endsWith(".LivingEntity")) continue;
                getInv = m;
                break;
            }
            if (getInv == null) return Boolean.FALSE;

            Class<?> handlerIface = Class.forName("top.theillusivec4.curios.api.type.inventory.ICuriosItemHandler");
            Method getCurios = handlerIface.getMethod("getCurios");

            Class<?> stacksHandlerIface = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Method getStacks = stacksHandlerIface.getMethod("getStacks");

            // The return type implements IItemHandler — either Forge's or NeoForge's package.
            // Rather than committing to one, resolve via reflection on the concrete return type.
            // We'll look up getSlots() / getStackInSlot(int) via the runtime instance.

            // Determine return-type unwrap path: LazyOptional vs direct.
            boolean lazy = getInv.getReturnType().getName().endsWith(".LazyOptional");
            Method resolve = null;
            if (lazy) {
                resolve = getInv.getReturnType().getMethod("resolve");
            } else if (!getInv.getReturnType().getName().endsWith(".Optional")) {
                // NeoForge 1.21 Curios returns Optional<ICuriosItemHandler> directly in newer builds.
                // Anything else: bail.
                if (!getInv.getReturnType().equals(handlerIface)) {
                    // Unknown return type — fail-closed.
                    return Boolean.FALSE;
                }
            }

            curiosApiGetInventory = getInv;
            returnsLazyOptional = lazy;
            lazyOptionalResolve = resolve;
            iCuriosItemHandlerGetCurios = getCurios;
            iCurioStacksHandlerGetStacks = getStacks;
            // iItemHandlerGetSlots / iItemHandlerGetStackInSlot resolved lazily off real instance.

            VonixServerUtilities.LOGGER.info(
                    "[VonixSU] Curios soft-dep probe OK. /backsee will scan curio slots.");
            return Boolean.TRUE;
        } catch (Throwable ignored) {
            // Curios not present, or its API moved — fail closed silently.
            return Boolean.FALSE;
        }
    }

    /**
     * Walk every Curios slot on {@code player} and return every non-empty
     * stack. Returns {@link Optional#empty()} when Curios is absent;
     * returns {@code Optional.of(emptyList)} when Curios is present but the
     * player has no curio slots populated.
     */
    public static Optional<List<ItemStack>> getCuriosStacks(ServerPlayer player) {
        if (player == null) return Optional.empty();
        if (!isAvailable()) return Optional.empty();
        try {
            Object invRaw = curiosApiGetInventory.invoke(null, player);
            if (invRaw == null) return Optional.of(Collections.emptyList());

            Object handler;
            if (returnsLazyOptional) {
                Object opt = lazyOptionalResolve.invoke(invRaw);
                if (!(opt instanceof Optional<?> o) || o.isEmpty()) return Optional.of(Collections.emptyList());
                handler = o.get();
            } else if (invRaw instanceof Optional<?> o) {
                if (o.isEmpty()) return Optional.of(Collections.emptyList());
                handler = o.get();
            } else {
                handler = invRaw;
            }
            if (handler == null) return Optional.of(Collections.emptyList());

            Object map = iCuriosItemHandlerGetCurios.invoke(handler);
            if (!(map instanceof java.util.Map<?, ?> curiosMap)) return Optional.of(Collections.emptyList());

            List<ItemStack> out = new ArrayList<>();
            for (Object stacksHandler : curiosMap.values()) {
                if (stacksHandler == null) continue;
                Object stacks = iCurioStacksHandlerGetStacks.invoke(stacksHandler);
                if (stacks == null) continue;
                Method getSlots = iItemHandlerGetSlots;
                Method getStack = iItemHandlerGetStackInSlot;
                if (getSlots == null || getStack == null) {
                    Class<?> sc = stacks.getClass();
                    getSlots = findMethod(sc, "getSlots");
                    getStack = findMethod(sc, "getStackInSlot", int.class);
                    if (getSlots == null || getStack == null) continue;
                    iItemHandlerGetSlots = getSlots;
                    iItemHandlerGetStackInSlot = getStack;
                }
                int slots;
                try { slots = (int) getSlots.invoke(stacks); }
                catch (Throwable t) { continue; }
                for (int i = 0; i < slots; i++) {
                    try {
                        Object s = getStack.invoke(stacks, i);
                        if (s instanceof ItemStack st && !st.isEmpty()) {
                            out.add(st);
                        }
                    } catch (Throwable t) {
                        // skip this slot; continue
                    }
                }
            }
            return Optional.of(out);
        } catch (Throwable t) {
            warnOnce(t);
            return Optional.empty();
        }
    }

    /**
     * Resolve all Curios slots, including empty slots, as a writable VSU-side
     * view.  The returned adapter keeps the concrete Curios handler references
     * so the accessory command can edit the real inventory rather than a copy.
     */
    public static Optional<CuriosInventory> resolveInventory(ServerPlayer player) {
        if (player == null || !isAvailable()) return Optional.empty();
        try {
            Object invRaw = curiosApiGetInventory.invoke(null, player);
            if (invRaw == null) return Optional.empty();

            Object handler;
            if (returnsLazyOptional) {
                Object opt = lazyOptionalResolve.invoke(invRaw);
                if (!(opt instanceof Optional<?> o) || o.isEmpty()) return Optional.empty();
                handler = o.get();
            } else if (invRaw instanceof Optional<?> o) {
                if (o.isEmpty()) return Optional.empty();
                handler = o.get();
            } else {
                handler = invRaw;
            }
            if (handler == null) return Optional.empty();

            Object map = iCuriosItemHandlerGetCurios.invoke(handler);
            if (!(map instanceof java.util.Map<?, ?> curiosMap)) return Optional.empty();

            List<Slot> slots = new ArrayList<>();
            for (Object stacksHandler : curiosMap.values()) {
                if (stacksHandler == null) continue;
                Object stacks = iCurioStacksHandlerGetStacks.invoke(stacksHandler);
                if (stacks == null) continue;
                Method getSlots = findMethod(stacks.getClass(), "getSlots");
                Method getStack = findMethod(stacks.getClass(), "getStackInSlot", int.class);
                Method setStack = findMethod(stacks.getClass(), "setStackInSlot", int.class, ItemStack.class);
                if (getSlots == null || getStack == null) continue;

                int count = (int) getSlots.invoke(stacks);
                for (int slot = 0; slot < count; slot++) {
                    slots.add(new Slot(stacks, slot, getStack, setStack));
                }
            }
            return Optional.of(new CuriosInventory(slots));
        } catch (Throwable t) {
            warnOnce(t);
            return Optional.empty();
        }
    }

    public static final class CuriosInventory {
        private final List<Slot> slots;

        private CuriosInventory(List<Slot> slots) {
            this.slots = List.copyOf(slots);
        }

        public int getSlots() { return slots.size(); }

        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= slots.size()) return ItemStack.EMPTY;
            return slots.get(slot).getStackInSlot();
        }

        public boolean setStackInSlot(int slot, ItemStack stack) {
            if (slot < 0 || slot >= slots.size()) return false;
            return slots.get(slot).setStackInSlot(stack);
        }

        public boolean isModifiable() {
            return !slots.isEmpty() && slots.stream().allMatch(Slot::isModifiable);
        }
    }

    private static final class Slot {
        private final Object handler;
        private final int slot;
        private final Method getStack;
        private final Method setStack;

        private Slot(Object handler, int slot, Method getStack, Method setStack) {
            this.handler = handler;
            this.slot = slot;
            this.getStack = getStack;
            this.setStack = setStack;
        }

        private ItemStack getStackInSlot() {
            try {
                Object value = getStack.invoke(handler, slot);
                return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
            } catch (Throwable t) {
                warnOnce(t);
                return ItemStack.EMPTY;
            }
        }

        private boolean setStackInSlot(ItemStack stack) {
            if (setStack == null) return false;
            try {
                setStack.invoke(handler, slot, stack);
                return true;
            } catch (Throwable t) {
                warnOnce(t);
                return false;
            }
        }

        private boolean isModifiable() { return setStack != null; }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null) {
            try { return c.getMethod(name, params); } catch (NoSuchMethodException ignored) {}
            c = c.getSuperclass();
        }
        // try interfaces
        for (Class<?> ifc : cls.getInterfaces()) {
            try { return ifc.getMethod(name, params); } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static void warnOnce(Throwable t) {
        if (warnedFault.compareAndSet(false, true)) {
            VonixServerUtilities.LOGGER.warn(
                    "[VonixSU] Curios bridge reflective call failed (will not warn again this run): {}",
                    t.toString());
        }
    }
}
