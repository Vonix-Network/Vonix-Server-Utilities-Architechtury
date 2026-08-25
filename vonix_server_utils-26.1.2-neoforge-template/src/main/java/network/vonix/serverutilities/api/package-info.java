/**
 * Public, SemVer-stable SPI surface for Vonix Server Utilities.
 *
 * <p>This is the only package within VSU treated as a public contract. Everything else
 * ({@code inventory}, {@code command}, {@code donation_ranks}, etc.) is internal and may
 * change without notice between minor releases.
 *
 * <p>Versioning policy:
 * <ul>
 *   <li><b>MAJOR</b> (2.0.0, 3.0.0, …) — breaking changes allowed (interface methods removed
 *       or signatures changed).</li>
 *   <li><b>MINOR</b> (1.x.0) — additive only (new interfaces, new optional default methods).</li>
 *   <li><b>PATCH</b> (1.x.y) — bug fixes and documentation only.</li>
 * </ul>
 *
 * <p>Primary entry points: {@link network.vonix.serverutilities.api.InventoryProvider},
 * {@link network.vonix.serverutilities.api.InventoryView},
 * {@link network.vonix.serverutilities.api.InventoryProviderRegistry}.
 *
 * @since 1.5.0
 */
package network.vonix.serverutilities.api;
