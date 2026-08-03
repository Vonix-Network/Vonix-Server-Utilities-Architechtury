package network.vonix.serverutilities.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A live-view Container backed directly by a ServerPlayer's inventory.
 *
 * Slot layout (matches a standard 6-row double chest / ChestMenu.sixRows):
 *   GUI slots  0–26  →  player main inventory rows 1–3  (inv slots 9–35)
 *   GUI slots 27–35  →  player hotbar                   (inv slots 0–8)
 *   GUI slots 36–39  →  player armor (feet→head)        (inv slots 100–103 / armor list 0–3)
 *   GUI slot  40     →  offhand                         (inv slot 40)
 *   GUI slots 41–53  →  empty / padding
 *
 * Items placed/removed through this container are written directly to the
 * target's Inventory so changes persist without a manual sync step.
 */
public class InvseeContainer implements Container {

    private final ServerPlayer target;

    public InvseeContainer(ServerPlayer target) {
        this.target = target;
    }

    /* ── size ─────────────────────────────────────────────────────── */

    @Override
    public int getContainerSize() {
        return 54; // 6 rows × 9 columns
    }

    /* ── slot mapping ─────────────────────────────────────────────── */

    /** Map a GUI slot index to the underlying Inventory slot index. */
    private int toInvSlot(int guiSlot) {
        if (guiSlot < 27) return guiSlot + 9;   // main inv rows 1–3
        if (guiSlot < 36) return guiSlot - 27;  // hotbar
        if (guiSlot < 40) return 100 + (guiSlot - 36); // armor (handled below)
        if (guiSlot == 40) return 40;            // offhand
        return -1;                                // padding
    }

    /* ── Container API ────────────────────────────────────────────── */

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 27) return target.getInventory().items.get(slot + 9);
        if (slot < 36) return target.getInventory().items.get(slot - 27);
        if (slot < 40) return target.getInventory().armor.get(slot - 36); // armor: 36=feet,37=legs,38=chest,39=head
        if (slot == 40) return target.getInventory().offhand.get(0);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack.split(amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 27) {
            ItemStack old = target.getInventory().items.get(slot + 9).copy();
            target.getInventory().items.set(slot + 9, ItemStack.EMPTY);
            return old;
        }
        if (slot < 36) {
            ItemStack old = target.getInventory().items.get(slot - 27).copy();
            target.getInventory().items.set(slot - 27, ItemStack.EMPTY);
            return old;
        }
        if (slot < 40) {
            ItemStack old = target.getInventory().armor.get(slot - 36).copy();
            target.getInventory().armor.set(slot - 36, ItemStack.EMPTY);
            return old;
        }
        if (slot == 40) {
            ItemStack old = target.getInventory().offhand.get(0).copy();
            target.getInventory().offhand.set(0, ItemStack.EMPTY);
            return old;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 27) {
            target.getInventory().items.set(slot + 9, stack);
        } else if (slot < 36) {
            target.getInventory().items.set(slot - 27, stack);
        } else if (slot < 40) {
            target.getInventory().armor.set(slot - 36, stack);
        } else if (slot == 40) {
            target.getInventory().offhand.set(0, stack);
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        target.getInventory().setChanged();
        // Force a full inventory update to the target player's client
        target.inventoryMenu.sendAllDataToRemote();
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // admin always has access
    }

    @Override
    public void clearContent() {
        // Intentionally no-op — don't wipe the target's inventory via the GUI API
    }
}
