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
 *   GUI slots 36–39  →  player armor (feet→head)        (armor list 0–3)
 *   GUI slot  40     →  offhand                         (offhand list 0)
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

    /* ── Container API ────────────────────────────────────────────── */

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 27) return target.getInventory().getItem(slot + 9);
        if (slot < 36) return target.getInventory().getItem(slot - 27);
        if (slot < 40) return target.getItemBySlot(new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD}[slot - 36]);
        if (slot == 40) return target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
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
            ItemStack old = target.getInventory().getItem(slot + 9).copy();
            target.getInventory().setItem(slot + 9, ItemStack.EMPTY);
            return old;
        }
        if (slot < 36) {
            ItemStack old = target.getInventory().getItem(slot - 27).copy();
            target.getInventory().setItem(slot - 27, ItemStack.EMPTY);
            return old;
        }
        if (slot < 40) {
            var equipment = new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD}[slot - 36];
            ItemStack old = target.getItemBySlot(equipment).copy();
            target.setItemSlot(equipment, ItemStack.EMPTY);
            return old;
        }
        if (slot == 40) {
            ItemStack old = target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).copy();
            target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            return old;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 27) {
            target.getInventory().setItem(slot + 9, stack);
        } else if (slot < 36) {
            target.getInventory().setItem(slot - 27, stack);
        } else if (slot < 40) {
            target.setItemSlot(new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD}[slot - 36], stack);
        } else if (slot == 40) {
            target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, stack);
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        target.getInventory().setChanged();
        target.inventoryMenu.sendAllDataToRemote();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        // Intentionally no-op
    }
}
