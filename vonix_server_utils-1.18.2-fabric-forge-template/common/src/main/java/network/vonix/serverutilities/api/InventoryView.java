package network.vonix.serverutilities.api;

import net.minecraft.world.item.ItemStack;

/**
 * Adapter returned by {@link InventoryProvider#resolve} representing the inventory view
 * VSU should open as a six-row chest GUI on behalf of the admin.
 *
 * <p>VSU drives a {@code SimpleContainer} of {@link #getSlots()} slots and mirrors writes
 * back via {@link #setStackInSlot(int, ItemStack)} + {@link #persist()} on each container
 * change. Implementations own the persistence path back to the underlying storage.
 *
 * <p>Part of the {@code network.vonix.serverutilities.api} published SPI — SemVer stable.
 */
public interface InventoryView {

    /** Number of slots in the underlying inventory. The GUI is always six rows (54 slots); excess GUI slots are ignored. */
    int getSlots();

    ItemStack getStackInSlot(int slot);

    void setStackInSlot(int slot, ItemStack stack);

    /** Called after writes are mirrored back. Must persist to the underlying source (item NBT, capability, component, etc.). */
    void persist();

    /** Display name shown as the chest GUI title. */
    String getTitle();
}
