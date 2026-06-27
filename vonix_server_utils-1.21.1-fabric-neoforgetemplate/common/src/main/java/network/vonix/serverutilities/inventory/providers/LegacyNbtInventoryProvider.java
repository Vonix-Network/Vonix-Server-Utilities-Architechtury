package network.vonix.serverutilities.inventory.providers;

import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.api.InventoryProvider;
import network.vonix.serverutilities.api.InventoryView;

import java.util.Optional;

/**
 * 1.21.1 NeoForge stub. The legacy NBT layout (raw {@code Items} / {@code inventory} /
 * {@code BlockEntityTag.Items} compound lists on the {@link net.minecraft.world.item.ItemStack}'s
 * NBT tag) was removed when 1.20.5 migrated item-level storage to
 * {@link net.minecraft.core.component.DataComponents}.
 *
 * <p>Modern shulker / bundle / container-item behaviour on 1.21.1 is covered by
 * {@link DataComponentsInventoryProvider}; the {@link CapabilityInventoryProvider}
 * handles capability-exposing modded backpacks.
 *
 * <p>The provider is kept registered (same id / priority as on 1.18.2 / 1.19.2 / 1.20.1)
 * so a single 3rd-party {@code META-INF/services} registration list works across every
 * VSU template — and so user expectations about pass-ordering hold whichever MC version
 * they target. {@code resolve} always returns {@link Optional#empty()}.
 *
 * <p>Priority 300 — same as on older targets.
 */
public final class LegacyNbtInventoryProvider implements InventoryProvider {

    public static final String ID = "vonix:legacy_nbt";
    public static final int    PRIORITY = 300;

    @Override public String id()       { return ID; }
    @Override public int    priority() { return PRIORITY; }

    @Override
    public Optional<InventoryView> resolve(ServerPlayer target, int slotHint) {
        return Optional.empty();
    }
}
