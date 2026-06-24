# FIX-metadata — Metadata + Mixin Wiring Fixes

Applied against the 4-template Architectury monorepo. No Java source was edited (only `TestMe.java` was deleted per task instructions). No `gradle` was run. Operator should spot-check on Windows.

---

## Fix 1 — 1.18.2 Forge modId mismatch (CRITICAL)

**File:** `vonix_server_utils-1.18.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml:7`

```
- modId = "vonix_server_utils"
+ modId = "vonix_server_utilities"
```

Also rewrote the three `[[dependencies.vonix_server_utils]]` table headers to `[[dependencies.vonix_server_utilities]]` so they continue to attach to the renamed mod block (TOML requires the suffix to match `modId`). Lines 14, 21, 28.

**fabric.mod.json:** `vonix_server_utils-1.18.2-fabric-forge-template/fabric/src/main/resources/fabric.mod.json:3` already has `"id": "vonix_server_utilities"` — no change needed. (The `mixins` ref on line 23 still says `vonix_server_utils.mixins.json`; see Fix 4 — left as-is and flagged.)

---

## Fix 2 — Forge mods.toml version ranges

### 1.18.2 (`.../1.18.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml:24`)
Confirmed `loaderVersion = "[40,)"`. Tightened MC range:
```
- versionRange = "[1.18.2,)"
+ versionRange = "[1.18.2,1.19)"
```

### 1.19.2 (`.../1.19.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml`)
On inspection, `loaderVersion` was already `[43,)` (line 2) and the forge dep `versionRange` was already `[43,)` — **the audit's claim of `[40,)` no longer matches the current file**. Only the minecraft range was stale:
```
- versionRange = "[1.19.2,)"
+ versionRange = "[1.19.2,1.20)"
```

### 1.20.1 (`.../1.20.1-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml`)
Same situation: `loaderVersion` already `[47,)` (line 2), forge dep range already `[47,)`. Only minecraft range updated:
```
- versionRange = "[1.20.1,)"
+ versionRange = "[1.20.1,1.21)"
```

> **NOTE for operator:** the 1.19.2 and 1.20.1 Forge `loaderVersion` fields had apparently been fixed since AUDIT-wiring.md was written. Spot-check that line 2 of each file is correct (`[43,)` for 19, `[47,)` for 20). No edit needed; flagging in case the audit reflects the intended state.

---

## Fix 3 — Mixin config registration in Forge mods.toml

Appended at end of each file:
```
[[mixins]]
config = "vonix_server_utilities.mixins.json"
```

Files (all end-of-file append):
- `vonix_server_utils-1.18.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml` (new lines 35–36)
- `vonix_server_utils-1.19.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml` (new lines 33–34)
- `vonix_server_utils-1.20.1-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml` (new lines 33–34)

**1.21.1 (NeoForge):** skipped — `neoforge/src/main/resources/META-INF/neoforge.mods.toml:37-38` already contains the same `[[mixins]] config = "vonix_server_utilities.mixins.json"` block. No change.

---

## Fix 4 — Stale `vonix_server_utils.mixins.json`

Status per template:

| Template | stale file | new file | action |
|---|---|---|---|
| 1.18.2 | exists (`package = "network.vonix.utils.mixin"`) | **does NOT exist** | **NOT deleted** — flagged for manual review |
| 1.19.2 | exists | exists (`package = "network.vonix.serverutilities.mixin"`) | **deleted stale** |
| 1.20.1 | exists | exists (`package = "network.vonix.serverutilities.mixin"`) | **deleted stale** |
| 1.21.1 | n/a | exists | clean |

Files removed:
- `vonix_server_utils-1.19.2-fabric-forge-template/common/src/main/resources/vonix_server_utils.mixins.json`
- `vonix_server_utils-1.20.1-fabric-forge-template/common/src/main/resources/vonix_server_utils.mixins.json`

**⚠ MANUAL REVIEW REQUIRED — 1.18.2:**
- Only the stale `vonix_server_utils.mixins.json` exists (with non-existent package `network.vonix.utils.mixin`).
- `fabric.mod.json:23` references this stale filename.
- The Forge `mods.toml` mixins block added in Fix 3 references `vonix_server_utilities.mixins.json`, which **does not exist** in 1.18.2.
- Recommended manual action: create `vonix_server_utilities.mixins.json` (clone from the 1.19.2 one — `compatibilityLevel = "JAVA_17"`, `package = "network.vonix.serverutilities.mixin"`), update `fabric.mod.json` line 23 to point at it, then delete the stale file. We did **not** create or modify the JSONs in 1.18.2 because the task scope was metadata only and creating a new resource file was out of scope.

---

## Fix 5 — 1.21.1 `fabric_loader_version` typo

**File:** `vonix_server_utils-1.21.1-fabric-neoforgetemplate/gradle.properties:16`

The other three templates use `0.19.2`, but per the audit and current Fabric loader numbering for 1.21.1 (0.16.x line), bumped to `0.16.0` and left a comment for operator verification:

```
- fabric_loader_version = 0.18.4
+ # NOTE: 0.18.4 was a typo (predates 1.21 support); bumped to 0.16.0 (new numbering scheme) — verify against fabricmc.net
+ fabric_loader_version = 0.16.0
```

> **⚠ NOTE for operator:** verify `0.16.0` against fabricmc.net for current 1.21.1; bump if a newer 0.16.x is available. `fabric.mod.json:depends.fabricloader` in the 1.21.1 template still pins `>=0.18.4` — left as-is per task scope but should likely be `>=0.16.0` to match.

---

## Fix 6 — Delete `TestMe.java` debug leak in 1.21.1

Deleted: `vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java/network/vonix/serverutilities/command/TestMe.java`

---

## Files Modified Summary

Modified:
1. `vonix_server_utils-1.18.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml` (Fix 1, 2, 3)
2. `vonix_server_utils-1.19.2-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml` (Fix 2, 3)
3. `vonix_server_utils-1.20.1-fabric-forge-template/forge/src/main/resources/META-INF/mods.toml` (Fix 2, 3)
4. `vonix_server_utils-1.21.1-fabric-neoforgetemplate/gradle.properties` (Fix 5)

Deleted:
5. `vonix_server_utils-1.19.2-fabric-forge-template/common/src/main/resources/vonix_server_utils.mixins.json` (Fix 4)
6. `vonix_server_utils-1.20.1-fabric-forge-template/common/src/main/resources/vonix_server_utils.mixins.json` (Fix 4)
7. `vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java/network/vonix/serverutilities/command/TestMe.java` (Fix 6)

## Items Flagged for Operator Review

- **1.18.2 mixin config:** `vonix_server_utilities.mixins.json` does not exist; only the stale `vonix_server_utils.mixins.json` (wrong package). `fabric.mod.json` and the new Forge mixins block both need a target file — see Fix 4 details.
- **1.21.1 `fabric_loader_version = 0.16.0`** — best-effort guess; verify against fabricmc.net.
- **1.21.1 `fabric.mod.json` `depends.fabricloader = ">=0.18.4"`** — inconsistent with the bumped gradle property; consider updating to `>=0.16.0`.
- **1.19.2 & 1.20.1 Forge `loaderVersion`** — already `[43,)` / `[47,)` in the current tree (audit may be stale). Confirm on disk.
