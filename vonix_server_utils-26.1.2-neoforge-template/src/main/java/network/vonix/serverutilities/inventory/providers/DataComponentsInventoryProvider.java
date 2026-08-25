package network.vonix.serverutilities.inventory.providers;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import network.vonix.serverutilities.api.InventoryProvider;
import network.vonix.serverutilities.api.InventoryView;

import java.util.Optional;

/**
 * Built-in provider (1.21+): wraps the vanilla {@code DataComponents.CONTAINER}
 * inventory exposed by shulker boxes, bundles, and similar items.
 *
 * <p>Priority 150 — runs between Curios (100) and Capability (200).
 */
public final class DataComponentsInventoryProvider implements InventoryProvider {

    public static final String ID = "vonix:data_components";
    public static final int PRIORITY = 150;

    @Override public String id()      { return ID; }
    @Override public int    priority(){ return PRIORITY; }

    @Override
    public Optional<InventoryView> resolve(ServerPlayer target, int slotHint) {
        int start = slotHint >= 0 ? slotHint : 0;
        int end = slotHint >= 0
                ? Math.min(slotHint + 1, target.getInventory().getContainerSize())
                : target.getInventory().getContainerSize();

        for (int i = start; i < end; i++) {
            ItemStack stack = target.getInventory().getItem(i);
            if (!stack.has(DataComponents.CONTAINER)) continue;
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

            final int slotIndex = i;
            final ItemStack finalStack = stack;
            final int gui = 54;
            final ItemStack[] slots = new ItemStack[gui];
            for (int j = 0; j < gui; j++) slots[j] = ItemStack.EMPTY;
            if (contents != null) {
                NonNullList<ItemStack> items = NonNullList.withSize(gui, ItemStack.EMPTY);
                contents.copyInto(items);
                for (int j = 0; j < items.size(); j++) slots[j] = items.get(j).copy();
            }
            final ServerPlayer fTarget = target;

            return Optional.of(new InventoryView() {
                @Override public int getSlots() { return gui; }
                @Override public ItemStack getStackInSlot(int s) { return slots[s]; }
                @Override public void setStackInSlot(int s, ItemStack st) { slots[s] = st; }
                @Override public void persist() {
                    java.util.List<ItemStack> list = new java.util.ArrayList<>();
                    for (int j = 0; j < gui; j++) list.add(slots[j]);
                    finalStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
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
