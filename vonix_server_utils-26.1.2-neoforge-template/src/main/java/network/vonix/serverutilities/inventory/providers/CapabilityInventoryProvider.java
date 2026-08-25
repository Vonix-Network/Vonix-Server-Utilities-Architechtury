package network.vonix.serverutilities.inventory.providers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import network.vonix.serverutilities.api.InventoryProvider;
import network.vonix.serverutilities.api.InventoryView;
import network.vonix.serverutilities.inventory.CapabilityInventoryBridge;

import java.util.Optional;

/**
 * Built-in provider: walks the target player's main inventory ({@code slotHint &lt; 0})
 * or one specific slot ({@code slotHint &gt;= 0}) looking for a stack that exposes a
 * writable {@code IItemHandler} capability via reflection.
 *
 * <p>Covers Sophisticated Backpacks/Storage, vanilla shulker (via Forge default cap
 * provider), Iron Chests shulker, Traveler's Backpack, Iron Backpacks,
 * FunctionalStorage drawers-as-item, etc.
 *
 * <p>Priority 200 — runs after {@link CuriosInventoryProvider} and (on 1.21+)
 * {@link DataComponentsInventoryProvider}, before {@link LegacyNbtInventoryProvider}.
 */
public final class CapabilityInventoryProvider implements InventoryProvider {

    public static final String ID = "vonix:capability";
    public static final int PRIORITY = 200;

    @Override public String id()      { return ID; }
    @Override public int    priority(){ return PRIORITY; }

    @Override
    public Optional<InventoryView> resolve(ServerPlayer target, int slotHint) {
        int startSlot = slotHint >= 0 ? slotHint : 0;
        int endSlotEx = slotHint >= 0
                ? Math.min(slotHint + 1, target.getInventory().getContainerSize())
                : target.getInventory().getContainerSize();

        for (int i = startSlot; i < endSlotEx; i++) {
            ItemStack stack = target.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            Optional<CapabilityInventoryBridge.Handler> handlerOpt = CapabilityInventoryBridge.resolve(stack);
            if (handlerOpt.isEmpty()) continue;
            final CapabilityInventoryBridge.Handler handler = handlerOpt.get();
            if (!handler.isModifiable()) continue; // read-only handler (rare) — let next provider try
            final int slotIndex = i;
            final ItemStack finalStack = stack;
            final int slots = handler.getSlots();
            final ServerPlayer fTarget = target;

            return Optional.of(new InventoryView() {
                @Override public int getSlots() { return slots; }
                @Override public ItemStack getStackInSlot(int slot) { return handler.getStackInSlot(slot); }
                @Override public void setStackInSlot(int slot, ItemStack stack) { handler.setStackInSlot(slot, stack); }
                @Override public void persist() {
                    fTarget.getInventory().setItem(slotIndex, finalStack);
                    fTarget.inventoryMenu.sendAllDataToRemote();
                }
                @Override public String getTitle() {
                    return "Backpack[" + slotIndex + "]: " + fTarget.getName().getString();
                }
            });
        }
        return Optional.empty();
    }
}
