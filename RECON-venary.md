# RECON — Venary Donation Ranks + Minecraft Server-Config Surface
_Read-only recon. Source: PostgreSQL `venary_beta` (peer auth) + `/var/www/VenaryService/server/modules/`._

---

## 1. `donation_ranks` table schema

`\d donation_ranks`:

| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | text | NOT NULL | — |
| `name` | text | NOT NULL | — |
| `price` | double precision | NOT NULL | — |
| `color` | text |  | `'#ffffff'` |
| `icon` | text |  | `'⭐'` |
| `description` | text |  | — |
| `perks` | text |  | — |
| `luckperms_group` | text |  | — |
| `sort_order` | integer |  | 0 |
| `active` | integer |  | 1 |
| `created_at` | text |  | `now()::text` |
| `tebex_package_id` | text |  | — |
| `duration_days` | integer |  | 30 |
| `tier` | integer |  | 0 |

Indexes: `donation_ranks_pkey` PK(`id`); `donation_ranks_name_key` UNIQUE(`name`).
Referenced by: `donations.rank_id`, `user_ranks.rank_id`.

**No `slug` or `display_name` column** — `name` IS the display string (UNIQUE). `id` is a text key (e.g. `rank_supporter`). `luckperms_group` already present (LP integration concern partially solved). `tier` integer is the canonical ordering for upgrade/extend logic; `sort_order` is purely UI.

---

## 2. `user_ranks` + `rank_conversions` schemas

`\d user_ranks` — **users → ranks (1:1, current rank only)**:

| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | text | NOT NULL | — |
| `user_id` | text | NOT NULL | — |
| `rank_id` | text | NOT NULL | — |
| `active` | integer |  | 1 |
| `started_at` | text |  | `now()::text` |
| `granted_at` | text |  | — |
| `expires_at` | text |  | — |

Indexes: PK(`id`); `idx_user_ranks_active`(`active`); `idx_user_ranks_user`(`user_id`); **`user_ranks_user_id_key` UNIQUE(`user_id`)** — only ONE rank per user. FKs to `users.id`, `donation_ranks.id`.

`\d rank_conversions` — **conversion-lookup / audit (when a user upgrades, days remaining on old rank are credited)**:

| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | text | NOT NULL | — |
| `user_id` | text | NOT NULL | — |
| `from_rank_id` | text |  | — |
| `to_rank_id` | text | NOT NULL | — |
| `days_remaining` | integer |  | 0 |
| `converted_at` | text |  | `now()::text` |

Indexes: PK(`id`); `idx_rank_conversions_user`(`user_id`). FK `user_id → users.id`. **No FK on rank ids** (allows rank deletion without dropping conversion history).

→ **`user_ranks` is the live mapping. `rank_conversions` is the audit/credit-carry log.**

---

## 3. Row counts + non-sensitive samples

`SELECT COUNT(*) FROM donation_ranks;` → **3**.
`SELECT id, name, color, icon, sort_order, tier, duration_days, active FROM donation_ranks ORDER BY sort_order LIMIT 20;`:

| id | name | color | icon | sort_order | tier | duration_days | active |
|---|---|---|---|---|---|---|---|
| `rank_supporter` | Supporter | #ff8800 | ⭐ | 0 | 1 | 30 | 1 |
| `rank_patron`    | Patron    | #06b6d4 | ⭐ | 1 | 2 | 30 | 1 |
| `rank_omega`     | Omega     | #eab308 | ⭐ | 2 | 3 | 30 | 1 |

`SELECT COUNT(*) FROM user_ranks;` → **0**. (No granted ranks yet — clean greenfield for Phase 2.)

---

## 4. Files touching donation/rank logic

`grep -rln -i 'donation_rank|user_rank|enrichWithDonationRank' /var/www/VenaryService/server/modules/`:

1. `modules/auth/index.js` — defines `enrichWithDonationRank()` (line 26).
2. `modules/users/index.js` — line 17 inlines an `enrichWithDonationRank`-style JOIN onto a user record.
3. `modules/social/index.js` — lines 68, 117 inline `donation_rank` join (name only) on post feed.
4. `modules/donations/index.js` — public + admin rank CRUD routes (see below).
5. `modules/donations/_ranks.js` — shared helpers (`getRank`, `getActiveUserRank`, `checkRankPolicy`, `applyRankGrant`).
6. `modules/donations/tebex.js` — Tebex gateway, calls `_ranks.js` helpers.
7. `modules/minecraft/index.js` — `GET /verify` (line 333) inlines donation-rank lookup for the mod.

Route signatures (donation/rank relevant only):

**`modules/donations/index.js`** (`router.`):
- L43  `GET    /ranks` (public)
- L61  `GET    /me/rank` (authenticateToken)
- L81  `GET    /recent`
- L102 `GET    /status`
- L125 `POST   /checkout` (optionalAuth)
- L179 `POST   /custom-checkout` (optionalAuth)
- L222 `POST   /webhook/oxapay`
- L248 `GET    /verify/:id` (optionalAuth)
- L270 `POST   /admin/ranks` (authenticateToken + requireAdmin)
- L289 `PUT    /admin/ranks/:id` (authenticateToken + requireAdmin)
- L315 `DELETE /admin/ranks/:id` (authenticateToken + requireAdmin)
- L323 `POST   /admin/ranks/reorder` (authenticateToken + requireAdmin)
- L337 `GET    /admin/stats` (authenticateToken + requireAdmin)
- L354 `POST   /admin/grant-rank` (authenticateToken + requireAdmin)
- L385 `POST   /admin/donations/manual` (authenticateToken + requireAdmin)
- L480 `GET    /admin/log` (authenticateToken + requireAdmin)

**`modules/donations/tebex.js`**:
- L123 `POST /checkout` (authenticateToken)
- L518 `GET  /status` (admin)
- L541 `POST /admin/test-webhook` (admin)

`modules/users/index.js`, `modules/social/index.js` only READ the rank — no rank-mutation routes.

---

## 5. `modules/minecraft/index.js` routes

`validateApiKey(req)` (L42) — pulls `x-api-key` header (or `Authorization: Bearer …`), looks up `mc_servers.api_key`. `requireAdmin` (L35) checks JWT user role.

| L | Method | Path | Auth |
|---|---|---|---|
| 69  | GET    | `/servers` | `optionalAuth` |
| 95  | GET    | `/servers/:id/status` | none (public) |
| 116 | GET    | `/servers/:id` | none (public) |
| 128 | GET    | `/servers/:id/history/span` | none (public) |
| 142 | GET    | `/servers/:id/history` | none (public) |
| 210 | GET    | `/leaderboard/debug` | none |
| 221 | GET    | `/leaderboard/meta` | none |
| 231 | GET    | `/leaderboard` | none |
| 274 | GET    | `/account/:userId` | none |
| 289 | POST   | `/link` | `authenticateToken` |
| 318 | DELETE | `/link` | `authenticateToken` |
| 333 | GET    | `/verify` | `validateApiKey` (mod) |
| 369 | POST   | `/minecraft/login` | `validateApiKey` |
| 399 | POST   | `/minecraft/register` | `validateApiKey` |
| 426 | POST   | `/minecraft/register-direct` | `validateApiKey` |
| 477 | POST   | `/sync/stats` | `validateApiKey` |
| 549 | POST   | `/link/generate` | `validateApiKey` |
| 577 | GET    | `/admin/servers` | `authenticateToken + requireAdmin` |
| 586 | PUT    | `/admin/servers/reorder` | admin |
| 601 | POST   | `/admin/servers` | admin |
| 623 | PUT    | `/admin/servers/:id` | admin |
| 647 | DELETE | `/admin/servers/:id` | admin |
| 660 | POST   | `/admin/servers/:id/regenerate-key` | admin |
| 670 | PUT    | `/admin/users/:id/minecraft` | admin |

Public CORS allowlist (L23-32): `/servers`, `/servers/:id`, `/servers/:id/status`, `/servers/:id/history`, `/servers/:id/history/span`, `/leaderboard`, `/leaderboard/meta`.

---

## 6. `mc_servers` schema (api_key field excluded from quote)

| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | text | NOT NULL | — |
| `name` | text | NOT NULL | — |
| `address` | text | NOT NULL | — |
| `port` | integer |  | 25565 |
| `description` | text |  | — |
| `icon` | text |  | — |
| `version` | text |  | — |
| `modpack_name` | text |  | — |
| `curseforge_url` | text |  | — |
| `modrinth_url` | text |  | — |
| `bluemap_url` | text |  | — |
| `api_key` | text | NOT NULL | — _(redacted)_ |
| `hide_port` | integer |  | 0 |
| `is_bedrock` | integer |  | 0 |
| `sort_order` | integer |  | 0 |
| `created_at` | text |  | `now()::text` |
| `pterodactyl_server_id` | text |  | — |

Indexes: PK(`id`); UNIQUE(`api_key`); partial UNIQUE on `pterodactyl_server_id`.
Referenced by: `player_stats`, `uptime_history`, `velt_wiki_articles.related_mc_server_id`.

**Confirmed: NO `features`, `feature_flags`, `config`, or JSON column exists.** Per-server settings currently must live on separate flat columns or a new join table.

---

## 7. `linked_accounts` schema

| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | text | NOT NULL | — |
| `user_id` | text | NOT NULL | — |
| `minecraft_uuid` | text | NOT NULL | — |
| `minecraft_username` | text | NOT NULL | — |
| `linked_at` | text |  | `now()::text` |

Indexes: PK(`id`); `idx_linked_user`(`user_id`); `idx_linked_uuid`(`minecraft_uuid`); **UNIQUE(`minecraft_uuid`)**; **UNIQUE(`user_id`)** — strict 1:1.
FK: `user_id → users.id`.

→ **Mod path**: `minecraft_uuid` (dash-formatted via `formatUUID()` helper L50) → `linked_accounts.user_id` → `user_ranks.rank_id` → `donation_ranks.*`. Already implemented inline in `GET /verify` (L350-360) with `expires_at > NOW` guard.

---

## 8. `enrichWithDonationRank()` implementation

Defined in `modules/auth/index.js` L26-35:

```js
async function enrichWithDonationRank(user) {
    try {
        const r = await db.get(
            `SELECT dr.name, dr.color, dr.icon, ur.expires_at
               FROM user_ranks ur
               JOIN donation_ranks dr ON dr.id = ur.rank_id
              WHERE ur.user_id = ? AND ur.active = 1
              LIMIT 1`, [user.id]);
        if (r) user.donation_rank = r;
    } catch { /* table may not exist on a fresh install */ }
    ...
}
```

- **Returns on user**: `user.donation_rank = { name, color, icon, expires_at }` (NO id, NO luckperms_group, NO tier).
- **Expires_at handling**: ❌ **Ignored** — query selects on `active = 1` only; does NOT filter `expires_at > NOW`. A stale row with `active=1` but expired date will still be returned. (Contrast: `_ranks.getActiveUserRank()` _does_ check expiry in JS; `minecraft/verify` SQL _does_ filter expiry.) **This is an existing bug the mod path must compensate for or fix.**
- **All ranks vs top**: Returns at most **1 row** (`LIMIT 1`) — and schema enforces 1:1 via `user_ranks_user_id_key`. There is no concept of multiple concurrent ranks.
- **Caching**: ❌ None. Hit on every `/me`, `/refresh`, login.

Same module also attaches `minecraft_uuid` + `minecraft_username` from `linked_accounts` (L38-46) and role flags (L47+).

---

## 9. Existing admin routes for donation ranks

`modules/admin/index.js`: NO direct rank routes — only a `case 'donations':` SQL aggregate inside a stats dispatcher (L100-102). All rank admin lives in `modules/donations/index.js`:

- `POST   /api/donations/admin/ranks` (create)
- `PUT    /api/donations/admin/ranks/:id` (edit)
- `DELETE /api/donations/admin/ranks/:id` (delete)
- `POST   /api/donations/admin/ranks/reorder`
- `POST   /api/donations/admin/grant-rank` (manual grant)
- `GET    /api/donations/admin/stats`
- `GET    /api/donations/admin/log`
- `POST   /api/donations/admin/donations/manual`

All gated by `authenticateToken + requireAdmin`.

---

## 10. Migration numbering

`ls /var/www/VenaryService/server/db/migrations/`:

```
015_discord_ticket_intake.sql
016_push_subscriptions.sql
017_mobile_app.sql
018_velt_mcp.sql
019_velt_wiki_fts_and_ptero_mapping.sql
```

**⚠️ 019 IS ALREADY TAKEN** (`019_velt_wiki_fts_and_ptero_mapping.sql`). **Next free prefix is `020`.**

---

## 11. Verdict + design decisions for Phase 2

### Tables: NEW vs extend?

| Concern | Decision | Rationale |
|---|---|---|
| MC-specific rank metadata (e.g. mod permission set, chat prefix, particle FX) | **NEW table `donation_ranks_mc`** keyed by `donation_ranks.id` FK | Keeps web/donation purity; `donation_ranks` is shared with Tebex/OVGC; avoid column bloat. `luckperms_group` already lives on parent — don't dup. |
| Per-server feature flags (e.g. "enable rank prefix on this MC server", "enable stat sync") | **NEW table `mc_server_features`** (server_id, feature_key, enabled, config jsonb) — preferred over a single `features jsonb` column on `mc_servers` because it indexes per-flag & audits cleanly. Either works; Postgres jsonb on `mc_servers` is acceptable as v1. | `mc_servers` confirmed has no jsonb config column today. |
| Mod-side audit (player joins, rank pushes, command executions) | **NEW table `mc_audit`** (id, server_id, player_uuid, user_id nullable, event_type, payload jsonb, created_at) | No existing audit channel; `player_stats` is stats only, `uptime_history` is server-up only. |
| Fix `enrichWithDonationRank` expiry bug | **Patch existing function** (not migration) — add `AND (ur.expires_at IS NULL OR ur.expires_at > NOW())` and cache. | Pure code fix; no schema change. |

### Recommended migration `020_mc_ranks_and_features.sql` contents

- `CREATE TABLE donation_ranks_mc (rank_id text PK FK → donation_ranks(id) ON DELETE CASCADE, chat_prefix text, chat_suffix text, name_color text, join_message text, particle_effect text, mod_permissions jsonb DEFAULT '[]', updated_at text)`.
- `CREATE TABLE mc_server_features (id text PK, server_id text FK → mc_servers(id) ON DELETE CASCADE, feature_key text NOT NULL, enabled integer DEFAULT 1, config jsonb DEFAULT '{}', updated_at text, UNIQUE(server_id, feature_key))` + index on `server_id`.
- `CREATE TABLE mc_audit (id text PK, server_id text FK → mc_servers(id), player_uuid text, user_id text FK → users(id) NULL, event_type text NOT NULL, payload jsonb DEFAULT '{}', created_at text DEFAULT now()::text)` + indexes on `(server_id, created_at)`, `(player_uuid)`, `(user_id)`, `(event_type)`.
- Optional: backfill seed rows for the 3 existing ranks in `donation_ranks_mc` with defaults.

No alterations to `donation_ranks`, `user_ranks`, `linked_accounts`, `mc_servers` needed.

### Recommended NEW mod-facing routes (auth: `validateApiKey`)

| Method | Path | Purpose |
|---|---|---|
| GET  | `/api/minecraft/player/:uuid/rank` | Resolve UUID → user → active rank (with `donation_ranks_mc` metadata + expires_at-aware). |
| GET  | `/api/minecraft/player/:uuid/profile` | Bundle: linked user, rank, role, level — single round-trip on join (extension of existing `/verify`). |
| GET  | `/api/minecraft/server/features` | Self-introspect feature flags for the server whose api_key was presented. |
| POST | `/api/minecraft/audit` | Mod-batched event ingest → `mc_audit`. Batched body: `{ events: [...] }`. |
| GET  | `/api/minecraft/ranks/manifest` | Full rank catalog with MC-side metadata so the mod can render prefixes/colors offline. Cache-friendly. |

### Recommended NEW admin routes (auth: `authenticateToken + requireAdmin`)

- `GET  /api/donations/admin/ranks/:id/mc` — fetch MC metadata.
- `PUT  /api/donations/admin/ranks/:id/mc` — upsert MC metadata (chat prefix etc.).
- `GET  /api/minecraft/admin/servers/:id/features` — list flags.
- `PUT  /api/minecraft/admin/servers/:id/features/:key` — toggle/configure flag.
- `GET  /api/minecraft/admin/audit?server_id=&player_uuid=&event_type=&limit=` — paginated audit query.

### Recommended admin UI pages

- **Donation Ranks → MC tab**: per-rank prefix/suffix/color/particles/perms editor (extends existing rank editor in `/admin/donations`).
- **Minecraft Servers → Features panel**: per-server toggle grid (rank-prefix sync, stat sync, audit ingest, chat bridge).
- **Minecraft → Audit log**: filterable table backed by `mc_audit`.

### Conflicts with existing schema

- ✅ `name` IS the display name on `donation_ranks` (UNIQUE) → **do NOT add `display_name` to `donation_ranks_mc`**. Reuse `donation_ranks.name`.
- ✅ `luckperms_group` already exists on `donation_ranks` → **do NOT duplicate** in `donation_ranks_mc`. If MC mod needs LP group, it reads from parent.
- ✅ `color` exists on `donation_ranks` but is the SITE accent color; MC chat name-color is a separate concern → keep `name_color` on `donation_ranks_mc` (or document that it falls back to parent `color` if NULL).
- ✅ `icon` on `donation_ranks` is a single emoji for the site → keep separate `chat_prefix` for MC; don't reuse.
- ✅ `tier` + `sort_order` + `duration_days` + `expires_at` semantics already solid — Phase 2 should NOT touch them.
- ⚠️ `user_ranks` UNIQUE(`user_id`) means there is at most ONE active rank per user — any mod logic must NOT assume multi-rank stacks.
- ⚠️ `enrichWithDonationRank()` does not check `expires_at` — fix in code, document in PR; mod path (`/verify`) already does the right thing in SQL.
- ⚠️ Migration prefix **020** (NOT 019 — taken).
