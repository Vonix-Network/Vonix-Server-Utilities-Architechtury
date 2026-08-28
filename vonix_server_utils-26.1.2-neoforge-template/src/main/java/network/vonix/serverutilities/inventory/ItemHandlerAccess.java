package network.vonix.serverutilities.inventory;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Loader-owned optional access to item-stack inventories. The 26.1.2 native
 * NeoForge entrypoint installs a capability adapter.
 */
public interface ItemHandlerAccess {
    ItemHandlerAccess NOOP = stack -> Optional.empty();

    Optional<Handler> resolve(ItemStack stack);

    interface Handler {
        int getSlots();
        ItemStack getStackInSlot(int slot);
        boolean setStackInSlot(int slot, ItemStack stack);
        boolean isModifiable();
    }

    final class Holder {
        private static volatile ItemHandlerAccess instance = NOOP;
        private Holder() {}
        public static void install(ItemHandlerAccess value) {
            instance = value != null ? value : NOOP;
        }
        public static ItemHandlerAccess get() {
            return instance;
        }
    }
}
