package network.vonix.serverutilities.inventory.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import network.vonix.serverutilities.inventory.CuriosInventoryBridge;

import java.util.Optional;

public final class AccessoryHelperImpl {
    private AccessoryHelperImpl() {}

    /**
     * Open a live, editable view of the target's Curios slots when the optional
     * Curios API is present.  The bridge owns all reflective access and writes
     * back to the real Curios handlers on every container change.
     */
    public static boolean openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        Optional<CuriosInventoryBridge.CuriosInventory> inventoryOpt =
                CuriosInventoryBridge.resolveInventory(target);
        if (inventoryOpt.isEmpty()) {
            viewer.sendSystemMessage(Component.literal(
                    "No Curios accessory inventory is available for " + target.getName().getString() + "."));
            return false;
        }

        CuriosInventoryBridge.CuriosInventory inventory = inventoryOpt.get();
        if (inventory.getSlots() == 0) {
            viewer.sendSystemMessage(Component.literal(
                    target.getName().getString() + " has no Curios accessory slots."));
            return false;
        }
        if (!inventory.isModifiable()) {
            viewer.sendSystemMessage(Component.literal(
                    "Curios accessory inventory for " + target.getName().getString() + " is read-only."));
            return false;
        }

        final int viewSlots = Math.min(inventory.getSlots(), 54);
        final boolean[] initializing = {true};
        SimpleContainer container = new SimpleContainer(54) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (initializing[0]) return;
                for (int slot = 0; slot < viewSlots; slot++) {
                    inventory.setStackInSlot(slot, getItem(slot));
                }
            }
        };

        for (int slot = 0; slot < viewSlots; slot++) {
            container.setItem(slot, inventory.getStackInSlot(slot));
        }
        initializing[0] = false;

        viewer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, playerInv, player) -> net.minecraft.world.inventory.ChestMenu.sixRows(id, playerInv, container),
                Component.literal("[ACCSEE] " + target.getName().getString())));
        return true;
    }
}
