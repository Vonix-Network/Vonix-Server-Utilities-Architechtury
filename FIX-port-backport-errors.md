# FIX: back-port errors in 1.18.2 / 1.19.2 / 1.20.1 templates

## 1.18.2 — `vonix_server_utils-1.18.2-fabric-forge-template`

- `common/src/main/java/network/vonix/serverutilities/admin/AdminManager.java`
  - Restored from `git show HEAD`. The previous port had overwritten this with 1.21-style
    `ClientboundPlayerInfoUpdatePacket` / `ClientboundPlayerInfoRemovePacket` imports.
    HEAD version uses the legacy single-packet `ClientboundPlayerInfoPacket(Action, ServerPlayer)`
    plus `new TextComponent(...)` + `sendMessage(..., Util.NIL_UUID)`, which is the correct API
    for MC 1.18.2.
- `common/src/main/java/network/vonix/serverutilities/kits/KitManager.java`
  - `import net.minecraft.core.registries.BuiltInRegistries;` → `import net.minecraft.core.Registry;`
  - All `BuiltInRegistries.ITEM` (lines 129, 206, 292) → `Registry.ITEM`.
- `common/src/main/java/network/vonix/serverutilities/command/UtilityCommands.java`
  - `import …ClientboundPlayerInfoUpdatePacket;` → `…ClientboundPlayerInfoPacket;`
  - `new ClientboundPlayerInfoUpdatePacket(…UPDATE_DISPLAY_NAME, player)`
    → `new ClientboundPlayerInfoPacket(…UPDATE_DISPLAY_NAME, player)`.

## 1.19.2 — `vonix_server_utils-1.19.2-fabric-forge-template`

- `common/src/main/java/network/vonix/serverutilities/admin/AdminManager.java`
  - Restored from `git show HEAD`. HEAD form uses legacy `ClientboundPlayerInfoPacket` (correct
    for 1.19.2) combined with `Component.literal(…) + sendSystemMessage(…)`.
  - NOTE on the drift table: the task spec grouped 1.19.2 with 1.18.2 for `Component.literal`,
    suggesting `new TextComponent(...)` should be used. That is **wrong for real 1.19.2** —
    `TextComponent` was removed in 1.19 and `Component.literal` was added in 1.19. The HEAD
    repo (and the rest of the 1.19.2 sources: UtilityCommands, WorldCommands, ModCommands,
    TeleportManager, LinkCommands, FeatureCommand) all use `Component.literal` + the new
    `sendSystemMessage`, so AdminManager was left in that form. Reverting it to `TextComponent`
    would break the entire template.
- `common/src/main/java/network/vonix/serverutilities/kits/KitManager.java`
  - `import …BuiltInRegistries;` → `import net.minecraft.core.Registry;`
  - All `BuiltInRegistries.ITEM` → `Registry.ITEM` (lines 129, 206, 292).
- `common/src/main/java/network/vonix/serverutilities/command/UtilityCommands.java`
  - `import …ClientboundPlayerInfoUpdatePacket;` → `…ClientboundPlayerInfoPacket;`
  - `new ClientboundPlayerInfoUpdatePacket(…UPDATE_DISPLAY_NAME, player)`
    → `new ClientboundPlayerInfoPacket(…UPDATE_DISPLAY_NAME, player)`.

## 1.20.1 — `vonix_server_utils-1.20.1-fabric-forge-template`

- `common/src/main/java/network/vonix/serverutilities/admin/AdminManager.java`
  - Restored from `git show HEAD` (still uses the newer `ClientboundPlayerInfoUpdatePacket` /
    `ClientboundPlayerInfoRemovePacket`, which is correct for 1.20.1).
  - **Additional fix** — the constructor call in the restored file used the 1.18-style
    signature `(Action, player)` which does not exist on the 1.20.1 `Update` packet.
    Corrected to `new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.ADD_PLAYER), List.of(player))`.
    (`EnumSet`/`List` are covered by the existing wildcard `java.util.*` import.)
  - `ClientboundPlayerInfoRemovePacket(List.of(uuid))` already correct — unchanged.
- `common/src/main/java/network/vonix/serverutilities/command/UtilityCommands.java`
  - Same constructor mismatch on `broadcastTabListUpdate`:
    `new ClientboundPlayerInfoUpdatePacket(Action.UPDATE_DISPLAY_NAME, player)`
    → `new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_DISPLAY_NAME), List.of(player))`.
    Used fully-qualified `java.util.EnumSet` / `java.util.List` to avoid touching imports.
- `KitManager.java` — left alone (1.20.1 has `BuiltInRegistries`).

## Verification scans (1.18.2 + 1.19.2)

```
grep -rn "BuiltInRegistries\." common/src/main/java          → 0 hits
grep -rn "ClientboundPlayerInfoUpdatePacket\|…RemovePacket"   → 0 hits
grep -rn "Component\.literal" common/src/main/java (1.18.2)   → 0 hits in code
                                                              (one comment line in LinkCommands.java)
grep -rn "Component\.literal" common/src/main/java (1.19.2)   → many hits, intentionally kept
                                                              (correct API for 1.19.2)
```

## Untouched, as required

- 1.21.1 template — not modified.
- `venary/`, `features/`, `donation_ranks/` directories — not modified in any template.
