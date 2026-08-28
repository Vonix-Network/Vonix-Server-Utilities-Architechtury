package network.vonix.serverutilities.inventory.neoforge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import network.vonix.serverutilities.inventory.ItemHandlerAccess;

import java.util.Optional;

/** Native NeoForge IItemHandler adapter for {@code /backsee}. */
public final class NeoForgeItemHandlerAccess implements ItemHandlerAccess {
    @Override
    public Optional<Handler> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM, null);
        if (handler == null || handler.getSlots() <= 0) return Optional.empty();
        boolean modifiable = handler instanceof IItemHandlerModifiable;
        return Optional.of(new NativeHandler(handler, modifiable));
    }

    private record NativeHandler(IItemHandler handler, boolean modifiable) implements Handler {
        @Override
        public int getSlots() {
            return handler.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack result = handler.getStackInSlot(slot);
            return result != null ? result : ItemStack.EMPTY;
        }

        @Override
        public boolean setStackInSlot(int slot, ItemStack stack) {
            if (!modifiable) return false;
            ((IItemHandlerModifiable) handler).setStackInSlot(slot, stack);
            return true;
        }

        @Override
        public boolean isModifiable() {
            return modifiable;
        }
    }
}
