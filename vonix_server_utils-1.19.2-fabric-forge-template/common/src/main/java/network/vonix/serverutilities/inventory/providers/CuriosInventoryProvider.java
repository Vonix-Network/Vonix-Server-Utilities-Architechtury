package network.vonix.serverutilities.inventory.providers;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import network.vonix.serverutilities.api.InventoryProvider;
import network.vonix.serverutilities.api.InventoryView;
import network.vonix.serverutilities.inventory.CapabilityInventoryBridge;
import network.vonix.serverutilities.inventory.CuriosInventoryBridge;

import java.util.List;
import java.util.Optional;

/**
 * Built-in provider: walks Curios-API slots on the target player looking for any item
 * stack that exposes a writable {@code IItemHandler} capability. Only runs when no
 * explicit slot was requested ({@code slotHint < 0}) — Curios slots are not part of the
 * player's main-inventory slot index space.
 *
 * <p>Soft-dep: fails closed when Curios isn't installed.
 *
 * <p>Priority 100 — runs before {@link DataComponentsInventoryProvider},
 * {@link CapabilityInventoryProvider} and {@link LegacyNbtInventoryProvider}.
 */
public final class CuriosInventoryProvider implements InventoryProvider {

    public static final String ID = "vonix:curios";
    public static final int PRIORITY = 100;

    @Override public String id()      { return ID; }
    @Override public int    priority(){ return PRIORITY; }

    @Override
    public Optional<InventoryView> resolve(ServerPlayer target, int slotHint) {
        if (slotHint >= 0) return Optional.empty(); // explicit slot = main-inv only

        Optional<List<ItemStack>> curiosOpt = CuriosInventoryBridge.getCuriosStacks(target);
        if (curiosOpt.isEmpty()) return Optional.empty();

        int curioIdx = 0;
        for (ItemStack curioStack : curiosOpt.get()) {
            if (curioStack == null || curioStack.isEmpty()) { curioIdx++; continue; }
            Optional<CapabilityInventoryBridge.Handler> chOpt = CapabilityInventoryBridge.resolve(curioStack);
            if (chOpt.isEmpty()) { curioIdx++; continue; }
            final CapabilityInventoryBridge.Handler handler = chOpt.get();
            if (!handler.isModifiable()) { curioIdx++; continue; }
            final int slots = handler.getSlots();
            final int curioLabel = curioIdx;
            final ServerPlayer fTarget = target;

            return Optional.of(new InventoryView() {
                @Override public int getSlots() { return slots; }
                @Override public ItemStack getStackInSlot(int slot) { return handler.getStackInSlot(slot); }
                @Override public void setStackInSlot(int slot, ItemStack stack) { handler.setStackInSlot(slot, stack); }
                @Override public void persist() {
                    // The Curios slot owns the stack — no main-inv writeback needed.
                    fTarget.inventoryMenu.sendAllDataToRemote();
                }
                @Override public String getTitle() {
                    return "Backpack[curio:" + curioLabel + "]: " + fTarget.getName().getString();
                }
            });
        }
        return Optional.empty();
    }
}
