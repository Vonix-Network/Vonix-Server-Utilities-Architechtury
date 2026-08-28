package network.vonix.serverutilities.inventory;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Common facade for {@code /backsee} item-handler inventories. Loader modules
 * install a native {@link ItemHandlerAccess}; Fabric keeps the no-op default.
 */
public final class CapabilityInventoryBridge {

    private CapabilityInventoryBridge() {}

    public static boolean isAvailable() {
        return ItemHandlerAccess.Holder.get() != ItemHandlerAccess.NOOP;
    }

    public static Optional<Handler> resolve(ItemStack stack) {
        return ItemHandlerAccess.Holder.get().resolve(stack).map(Handler::new);
    }

    /**
     * VSU-side handle to a resolved item inventory. No Forge / NeoForge types
     * leak through this surface.
     */
    public static final class Handler {
        private final ItemHandlerAccess.Handler inner;

        Handler(ItemHandlerAccess.Handler inner) {
            this.inner = inner;
        }

        public int getSlots() { return inner.getSlots(); }

        public ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        public boolean setStackInSlot(int slot, ItemStack stack) {
            return inner.setStackInSlot(slot, stack);
        }

        public boolean isModifiable() {
            return inner.isModifiable();
        }
    }
}
