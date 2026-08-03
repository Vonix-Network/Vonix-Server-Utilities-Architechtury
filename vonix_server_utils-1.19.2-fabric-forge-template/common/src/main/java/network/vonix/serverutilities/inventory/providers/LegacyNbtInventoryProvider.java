package network.vonix.serverutilities.inventory.providers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import network.vonix.serverutilities.api.InventoryProvider;
import network.vonix.serverutilities.api.InventoryView;

import java.util.Optional;

/**
 * Built-in provider: walks the target player's main inventory looking for items that
 * store their inventory in the legacy NBT layout — {@code Items}, {@code inventory}, or
 * {@code BlockEntityTag.Items} compound lists.
 *
 * <p>Fallback for items that store inventory in raw NBT without exposing the
 * {@code IItemHandler} capability. On 1.21+ the {@link DataComponentsInventoryProvider}
 * takes precedence for vanilla shulker/bundle behaviour.
 *
 * <p>Priority 300 — runs last among built-ins.
 */
public final class LegacyNbtInventoryProvider implements InventoryProvider {

    public static final String ID = "vonix:legacy_nbt";
    public static final int PRIORITY = 300;

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
            CompoundTag tag = stack.getTag();
            if (tag == null) continue;

            ListTag listTag = null;
            String location = null; // "Items" | "inventory" | "BlockEntityTag.Items"
            if (tag.contains("Items", 9)) {
                listTag = tag.getList("Items", 10);
                location = "Items";
            } else if (tag.contains("inventory", 9)) {
                listTag = tag.getList("inventory", 10);
                location = "inventory";
            } else if (tag.contains("BlockEntityTag", 10)) {
                CompoundTag bet = tag.getCompound("BlockEntityTag");
                if (bet.contains("Items", 9)) {
                    listTag = bet.getList("Items", 10);
                    location = "BlockEntityTag.Items";
                }
            }
            if (listTag == null) continue;

            final int slotIndex = i;
            final ItemStack finalStack = stack;
            final String fLocation = location;
            final int gui = 54;
            final ItemStack[] slots = new ItemStack[gui];
            for (int j = 0; j < gui; j++) slots[j] = ItemStack.EMPTY;
            for (int j = 0; j < listTag.size(); j++) {
                CompoundTag itemTag = listTag.getCompound(j);
                int s = itemTag.getByte("Slot") & 255;
                if (s >= 0 && s < gui) slots[s] = ItemStack.of(itemTag);
            }
            final ServerPlayer fTarget = target;

            return Optional.of(new InventoryView() {
                @Override public int getSlots() { return gui; }
                @Override public ItemStack getStackInSlot(int slot) { return slots[slot]; }
                @Override public void setStackInSlot(int slot, ItemStack stack) { slots[slot] = stack; }
                @Override public void persist() {
                    ListTag newList = new ListTag();
                    for (int j = 0; j < gui; j++) {
                        ItemStack it = slots[j];
                        if (!it.isEmpty()) {
                            CompoundTag itemTag = new CompoundTag();
                            itemTag.putByte("Slot", (byte) j);
                            it.save(itemTag);
                            newList.add(itemTag);
                        }
                    }
                    CompoundTag t = finalStack.getOrCreateTag();
                    switch (fLocation) {
                        case "BlockEntityTag.Items" -> t.getCompound("BlockEntityTag").put("Items", newList);
                        case "inventory"            -> t.put("inventory", newList);
                        default                     -> t.put("Items", newList);
                    }
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
