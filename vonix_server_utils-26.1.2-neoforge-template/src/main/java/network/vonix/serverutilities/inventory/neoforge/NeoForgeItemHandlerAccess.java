package network.vonix.serverutilities.inventory.neoforge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import network.vonix.serverutilities.inventory.ItemHandlerAccess;

import java.util.Optional;

/**
 * Native 26.1.2 adapter for {@code /backsee}. NeoForge 26.x item inventories
 * are {@code ResourceHandler<ItemResource>} queried through
 * {@code Capabilities.Item.ITEM} with {@link ItemAccess} as the context.
 */
public final class NeoForgeItemHandlerAccess implements ItemHandlerAccess {
    @Override
    public Optional<Handler> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ItemAccess access = ItemAccess.forStack(stack);
        ResourceHandler<ItemResource> handler = access.getCapability(Capabilities.Item.ITEM);
        if (handler == null || handler.size() <= 0) return Optional.empty();
        return Optional.of(new NativeHandler(handler));
    }

    private record NativeHandler(ResourceHandler<ItemResource> handler) implements Handler {
        @Override
        public int getSlots() {
            return handler.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack result = ItemUtil.getStack(handler, slot);
            return result != null ? result : ItemStack.EMPTY;
        }

        @Override
        public boolean setStackInSlot(int slot, ItemStack stack) {
            ItemResource desired = stack == null || stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
            int desiredAmount = stack == null || stack.isEmpty() ? 0 : stack.getCount();
            if (handler instanceof IndexModifier<?> raw) {
                @SuppressWarnings("unchecked")
                IndexModifier<ItemResource> modifier = (IndexModifier<ItemResource>) raw;
                modifier.set(slot, desired, desiredAmount);
                return true;
            }
            try (Transaction tx = Transaction.openRoot()) {
                ItemResource current = handler.getResource(slot);
                int currentAmount = handler.getAmountAsInt(slot);
                if (!current.isEmpty() && currentAmount > 0) {
                    int extracted = handler.extract(slot, current, currentAmount, tx);
                    if (extracted != currentAmount) return false;
                }
                if (!desired.isEmpty() && desiredAmount > 0) {
                    int inserted = handler.insert(slot, desired, desiredAmount, tx);
                    if (inserted != desiredAmount) return false;
                }
                tx.commit();
                return true;
            }
        }

        @Override
        public boolean isModifiable() {
            return true;
        }
    }
}
