# BUILD — Venary site-link integration (1.21.1 template)

Target: `vonix_server_utils-1.21.1-fabric-neoforgetemplate/`
Java 21, Architectury common module. No new Gradle deps — uses only
`java.net.http`, Gson (bundled with Minecraft), Architectury events, Brigadier.

## Files created

| Path | Lines |
|------|------:|
| `common/.../venary/VenaryConfig.java`    |  82 |
| `common/.../venary/VenaryClient.java`    | 186 |
| `common/.../venary/LinkCommands.java`    | 177 |
| `common/.../venary/PlayerSyncTask.java`  | 103 |

## Files modified

| Path | Lines (post-edit) | Change |
|------|------:|--------|
| `common/.../config/ModConfig.java`           | 134 | +7 Venary fields, boolOf/strOf helpers, defaults written into the auto-generated properties file, `getVenaryConfig()` factory. |
| `common/.../command/ModCommands.java`        | 596 | Added `LinkCommands` import; `showStatus` now lists `venary` as a module and calls `LinkCommands.appendStatusLines(source)`. |
| `common/.../listener/EventHandler.java`      |  88 | Registers `LinkCommands` on command-event; on `SERVER_STARTING` calls `VenaryClient.init(...)` and `PlayerSyncTask.register()`; on `SERVER_STOPPED` shuts down the HTTP client and clears state; join/quit hooks notify `PlayerSyncTask` and `LinkCommands`. |

### Note on `VonixServerUtilities.java`

The task brief listed `VonixServerUtilities.java` as a wiring point, but the
project's actual idiom is to do ALL lifecycle/event registrations inside
`EventHandler.init()` (which `VonixServerUtilities.init()` already calls).
Wiring Venary there matches existing managers exactly — no edit to
`VonixServerUtilities.java` was needed or appropriate.

## Config keys added (`config/vonix_server_utilities.properties`)

```
venary_enabled=false                    # master kill switch (default OFF)
venary_api_base=https://api.vonix.network
venary_api_key=                         # paste mc_servers.api_key here
venary_login_jwt_enabled=false          # /minecraft/login — default OFF
venary_stats_sync_enabled=false         # /minecraft/players/sync — default OFF
venary_stats_sync_interval_minutes=15
venary_link_cooldown_seconds=30
```

The defaults keep the entire HTTP layer dormant; the operator must explicitly
opt in. The API key is masked in every log line and in `/vonixsu status`.

## HTTP contract (VenaryClient)

* Singleton + dedicated 2-thread daemon executor (`VonixSU-Venary`).
* `java.net.http.HttpClient`, connect=5s, response=10s, no redirects.
* Every request: `POST <base><path>?protocol_version=1`, headers
  `Content-Type: application/json`, `Accept: application/json`,
  `x-api-key: <key>`, `User-Agent: VonixServerUtilities/<modver> MC/1.21.1`.
* **Fail-open**: every method returns a CompletableFuture that yields `null`
  on ANY failure (kill switch off, missing key, IO error, non-2xx, malformed
  JSON). Never throws to callers. Errors logged at DEBUG only, never with the
  API key.
* `loginJwt` and `syncPlayer` short-circuit to `null` when their respective
  feature flags are false.

## /link UX — exactly what the player sees

When `venary_enabled=true` and the server reaches Venary successfully:

```
[VSU] Requesting link code…
[VSU] Your Vonix link code:
    ABC123  (click to copy)            ← bold gold, hover "Click to copy",
                                          click action COPY_TO_CLIPBOARD
Go to: https://vonix.network/account/link-minecraft    ← aqua underlined,
                                          hover "Open in browser",
                                          click action OPEN_URL
Paste the code on the site to finish linking. Expires in 5 min.
```

Failure / disabled / cooldown:

| Condition | Message |
|-----------|---------|
| `venary_enabled=false` or client not initialised | `[VSU] Site link feature disabled by server admin.` |
| Within cooldown | `[VSU] Please wait Ns before requesting another link code.` |
| Venary unreachable / non-2xx / null body | `[VSU] Site unreachable, try again in a minute.` |

## /unlink UX

```
[VSU] To unlink your Minecraft account, sign in at
      vonix.network/account/link-minecraft  ← clickable OPEN_URL chip
      and click Unlink.
```

(Venary's DELETE /minecraft/link requires a user JWT, so in-game unlink is
intentionally not supported — confirmed against
`/var/www/VenaryService/server/modules/minecraft/index.js`.)

## /vonixsu status — added section

```
Venary: enabled, base=https://api.vonix.network, api_key=****wxyz
  login-jwt=false, stats-sync=false (every 15m), link-cooldown=30s
```

When the key is unset the value is rendered as `<unset>`. When fewer than 5
chars are configured the value is rendered as `****` (no leak).

## Port notes (1.20.1 / 1.19.2 / 1.18.2)

All API uses tagged inline with `// PORT-NOTE:` comments. Summary:

* `Component.literal` / `Style.EMPTY.withClickEvent(...).withHoverEvent(...)`
  — stable from 1.19.x onward. 1.18.2 needs `new TextComponent(...)` and the
  same Style builder.
* `ClickEvent.Action.COPY_TO_CLIPBOARD` and `OPEN_URL` — stable on all four
  versions.
* `TickEvent.SERVER_POST` (Architectury) — stable on all four versions.
* `java.net.http.HttpClient` — JDK 11+, identical on every target.
* No mixins, no Minecraft-version-locked registries used.

## What was deliberately NOT done

* No Gradle dependency changes.
* No edits to other version templates (1.18/1.19/1.20).
* No edits to existing managers (Home/Warp/Kit/Teleport/Admin) beyond the
  three-line `/vonixsu status` extension and event-handler wiring.
* No Venary backend or Astro site changes.
* No `gradle build` attempted — JDK 21 + MC toolchain unavailable on this
  host. Operator (WeedMeister) test-builds on Windows.

## Outstanding `TODO(operator-review)`

* `PlayerSyncTask` currently sends only `playtime_ticks` and
  `playtime_ticks_delta`. Health / XP / death counters are stubbed until the
  Venary stats schema is locked in. Add them in the marked TODO block.
