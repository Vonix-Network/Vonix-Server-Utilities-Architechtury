# VSU Moderation Guide

Operator workflow for the v1.6.0 moderation subsystem. For the command list see [COMMANDS.md § Moderation](COMMANDS.md#moderation); for permissions see [PERMISSIONS.md](PERMISSIONS.md).

## Table of contents

- [Duration syntax](#duration-syntax)
- [Escalation policy template](#escalation-policy-template)
- [Common workflows](#common-workflows)
  - [Tempban for harassment](#tempban-for-harassment)
  - [Mute for chat spam](#mute-for-chat-spam)
  - [Warning chain](#warning-chain)
  - [Mass cleanup](#mass-cleanup)
- [Bypass nodes](#bypass-nodes)
- [Audit log](#audit-log)
- [Restoring an erroneous ban or mute](#restoring-an-erroneous-ban-or-mute)
- [LuckPerms integration](#luckperms-integration)

## Duration syntax

Accepted by `/tempban`, `/tempmute`, and any other duration argument:

```
Units (case-insensitive, no spaces):
  s   second
  m   minute
  h   hour
  d   day
  w   week
  mo  month   (30 days)
  y   year    (365 days)

Permanent literals:
  perm  permanent  never

Composition:
  Concatenate without spaces, largest unit first.
  Examples:  1d12h     7d6h30m     2w3d

Rejected:
  - any token without a unit (e.g. "30")
  - negative values  (e.g. "-1d")
  - values exceeding 100 years
```

The parser returns milliseconds until expiry, or empty for permanent. Tab-complete suggests `1h, 6h, 1d, 7d, 30d, perm`.

## Escalation policy template

> This is a **template**, not enforced by the mod. Adjust thresholds to your community.

| Step | Action | When |
|---|---|---|
| 1 | `/warn` | First minor offence (mild spam, off-topic, mild swearing). |
| 2 | `/warn` | Second minor offence within 30 days. |
| 3 | `/tempmute 1h` | Third warning, or first chat-targeted offence. |
| 4 | `/tempmute 1d` | Repeat chat offence within 7 days of step 3. |
| 5 | `/tempban 1d` | First serious offence (harassment, slur, griefing). |
| 6 | `/tempban 7d` | Repeat serious offence within 30 days of step 5. |
| 7 | `/tempban 30d` | Repeat serious offence within 90 days of step 6. |
| 8 | `/ban` | Final straw — repeated bans, doxxing, cheating, evasion. |

Reset rule: warnings older than 90 days are usually ignored for escalation purposes. Use `/warnings <player>` to review.

## Common workflows

### Tempban for harassment

```
/warnings Steve
/tempban Steve 7d harassment of player Alex in chat — see warn #41
```

The player is kicked immediately with the reason and expiry shown on screen (`tellraw` precedes the disconnect by 1 second so they read it). Reconnect attempts are refused while `active=1` AND `now < expires_at`.

### Mute for chat spam

```
/tempmute Steve 1h chat spam — caps and repeated messages
```

Mute hooks `ServerChatEvent` (NeoForge) and `ServerMessageEvents.CHAT_MESSAGE` (Fabric). It also disables `/me`, `/msg`, `/r`, `/tell`, `/broadcast` for the muted player via a `.requires()` predicate. The player gets `§c[VSU] You are muted` with expiry on every blocked attempt.

`/tempmute` works on offline players — the row is inserted, the mute applies on next login.

### Warning chain

```
/warn Steve please use English in main chat
/warn Steve second reminder — English only in main chat
/warnings Steve
/tempmute Steve 30m third warning — see /warnings Steve
```

`/warnings <player>` paginates 10 per page. `/clearwarnings <player>` sets `active=0` on every `WARN` row for that target. **The rows are kept** — history is preserved for audit, see [Audit log](#audit-log).

### Mass cleanup

After an incident you may want to bulk-revoke a tempban issued by mistake, or list every active ban for a given moderator. Use SQLite directly — see [Audit log](#audit-log) for queries. Don't try to do bulk reversals from chat; revoke from SQL and notify ops.

## Bypass nodes

| Node | Effect | When to grant |
|---|---|---|
| `vsu.bypass.mute` | Exempts holder from mute enforcement. | Senior staff testing the mute system, or a bot account that must never be muted. |
| `vsu.bypass.ban` | Exempts holder from the join-time ban check. | **Never to a regular admin.** Use only on the owner account or a temporary `mod-test` group while validating ban behaviour. |

A muted/banned player who is granted the corresponding bypass node will appear muted/banned in `/banlist` and the database, but enforcement is silently skipped while the node is held. Revoking the node re-enables enforcement on next event.

## Audit log

All four punishment types — `BAN`, `MUTE`, `KICK`, `WARN` — write a row to the `punishments` table in the VSU SQLite database (co-located with the existing VSU DB; see your server's `world/data/` or VSU config for the exact path).

Schema:

```sql
CREATE TABLE punishments (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    type          TEXT NOT NULL,         -- 'BAN' | 'MUTE' | 'KICK' | 'WARN'
    target_uuid   TEXT NOT NULL,
    target_name   TEXT NOT NULL,         -- last-known name at time of issue
    issuer_uuid   TEXT,                  -- NULL = console
    issuer_name   TEXT NOT NULL,
    reason        TEXT,
    issued_at     INTEGER NOT NULL,      -- epoch millis
    expires_at    INTEGER,               -- epoch millis, NULL = permanent / N/A
    active        INTEGER NOT NULL DEFAULT 1,
    revoked_by    TEXT,
    revoked_at    INTEGER
);
```

### Useful queries

Active bans, newest first:

```sql
SELECT id, target_name, issuer_name, reason,
       datetime(issued_at/1000, 'unixepoch')  AS issued,
       datetime(expires_at/1000, 'unixepoch') AS expires
  FROM punishments
 WHERE type='BAN' AND active=1
 ORDER BY issued_at DESC;
```

Full punishment history for a player (`Steve`):

```sql
SELECT id, type, reason, issuer_name,
       datetime(issued_at/1000, 'unixepoch') AS issued,
       CASE WHEN expires_at IS NULL THEN 'perm'
            ELSE datetime(expires_at/1000, 'unixepoch') END AS expires,
       active, revoked_by,
       CASE WHEN revoked_at IS NULL THEN ''
            ELSE datetime(revoked_at/1000, 'unixepoch') END AS revoked
  FROM punishments
 WHERE target_name = 'Steve'
 ORDER BY issued_at DESC;
```

Everything a single moderator has issued in the last 30 days:

```sql
SELECT type, target_name, reason,
       datetime(issued_at/1000, 'unixepoch') AS issued
  FROM punishments
 WHERE issuer_name = 'AdminAlex'
   AND issued_at > (strftime('%s','now') - 30*86400) * 1000
 ORDER BY issued_at DESC;
```

Export a player's record to CSV:

```bash
sqlite3 -header -csv vsu.sqlite \
  "SELECT * FROM punishments WHERE target_name='Steve' ORDER BY issued_at;" \
  > steve-record.csv
```

## Restoring an erroneous ban or mute

Use `/unban <player>` and `/unmute <player>`. **The row is not deleted** — `active` is set to 0, `revoked_by` is set to the operator's name, and `revoked_at` is set to the current epoch millis. The original issuance row is preserved for audit. A new ban issued later starts a fresh row.

Example:

```
/unban Steve
```

If you need to revoke a punishment that the in-game commands can't reach (e.g. a corrupted `expires_at`), do it from SQL:

```sql
UPDATE punishments
   SET active     = 0,
       revoked_by = 'CONSOLE',
       revoked_at = strftime('%s','now') * 1000
 WHERE id = 87;
```

Then reload the cache (`/vonixsu reload`) or restart so the in-memory `MuteState`/ban cache picks it up.

To investigate an `/unban` after the fact:

```sql
SELECT id, target_name, reason, issuer_name, revoked_by,
       datetime(issued_at/1000,  'unixepoch') AS issued,
       datetime(revoked_at/1000, 'unixepoch') AS revoked
  FROM punishments
 WHERE type='BAN' AND active=0 AND revoked_by IS NOT NULL
 ORDER BY revoked_at DESC;
```

## LuckPerms integration

The two interesting moderation-time LP patterns:

**1. Temporary ban-bypass for testing.** Create a throwaway group that holds `vsu.bypass.ban`, attach it to a test alt for the duration of a debugging session, then remove:

```
lp creategroup ban-test
lp group ban-test permission set vsu.bypass.ban true
lp user TestAlt parent add ban-test
# ... do your testing ...
lp user TestAlt parent remove ban-test
lp deletegroup ban-test
```

**2. Trainee moderator with limited ban scope.** Grant `vsu.mod.kick` and `vsu.mod.warn` but explicitly deny `vsu.mod.ban` and `vsu.mod.mute` until they're trusted:

```
lp creategroup mod-trainee
lp group mod-trainee parent add builder
lp group mod-trainee permission set vsu.mod.kick true
lp group mod-trainee permission set vsu.mod.warn true
lp group mod-trainee permission set vsu.mod.ban false
lp group mod-trainee permission set vsu.mod.mute false
```

**Never** grant `vsu.bypass.ban` to a group inherited by regular admins. If it's needed for an emergency lockout test, use the throwaway-group pattern above and tear it down after.

## Related docs

- [COMMANDS.md](COMMANDS.md) — command reference
- [PERMISSIONS.md](PERMISSIONS.md) — node tree and group recipes
