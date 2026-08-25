package network.vonix.serverutilities.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Public SPI for resolving an "openable inventory" attached to an item on a target player.
 *
 * <p>Used by {@code /backsee} to dispatch through a priority-ordered chain of providers.
 * Third-party mods can ship their own {@link InventoryProvider} implementations either by:
 * <ol>
 *   <li>Explicit registration: {@link InventoryProviderRegistry#register(InventoryProvider)}
 *       during mod init.</li>
 *   <li>{@link java.util.ServiceLoader}: drop a file at
 *       {@code META-INF/services/network.vonix.serverutilities.api.InventoryProvider}
 *       listing the FQN of your implementation.</li>
 * </ol>
 *
 * <p>This interface is part of the {@code network.vonix.serverutilities.api} package, the
 * published, SemVer-stable surface of VSU. Breaking changes only in MAJOR.
 */
public interface InventoryProvider {

    /** Stable identifier — e.g. {@code "vonix:capability"}, {@code "vonix:curios"}, {@code "yourmod:custom"}. */
    String id();

    /**
     * Lower runs first. Built-in providers use:
     * <ul>
     *   <li>CURIOS = 100</li>
     *   <li>DATA_COMPONENTS = 150 (1.21+)</li>
     *   <li>CAPABILITY = 200</li>
     *   <li>LEGACY_NBT = 300</li>
     * </ul>
     */
    int priority();

    /**
     * Attempt to resolve an inventory view on {@code target}.
     *
     * @param target    the player whose items are being inspected.
     * @param slotHint  {@code -1} if the user passed no slot; {@code >= 0} if {@code /backsee <p> <slot>}.
     *                  Providers should respect the hint — when {@code >= 0}, scan only that main-inventory slot.
     * @return Empty if this provider can't handle anything on the target; present = caller opens it.
     */
    Optional<InventoryView> resolve(ServerPlayer target, int slotHint);
}
