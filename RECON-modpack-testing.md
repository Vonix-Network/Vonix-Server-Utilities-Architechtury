# RECON: Modpack server-pack testing feasibility

Recon pass only — **no modpacks downloaded**. One probe against CurseForge to confirm download mechanics, everything else estimated.

---

## 1. CurseForge server-pack download mechanics

**Probe result (Protocol Zero page):**

```
curl -sI -A 'Mozilla/5.0' https://www.curseforge.com/minecraft/modpacks/zombie-protocol-zero-a-modern-zombie-apocalypse
→ HTTP/2 403
   cf-mitigated: challenge
   server: cloudflare
```

CurseForge wraps the *entire* `curseforge.com` web surface in a Cloudflare interactive challenge. A plain curl / wget cannot reach the page, never mind the "Server Files" tab. So:

- **Scraping the HTML for a download button → BLOCKED.** Requires a real browser or a CF-solver (flaresolverr, etc.). Not worth standing up.
- **The CDN itself (`mediafilez.forgecdn.net` / `edge.forgecdn.net`) is open.** A 404 probe against `edge.forgecdn.net/files/0/0/test` returns a normal CloudFront 404, not a CF challenge. So *if* we know the numeric `fileId`, the URL pattern `https://edge.forgecdn.net/files/<aaa>/<bbb>/<filename>` downloads without auth.
- **Getting the fileId headlessly requires the CurseForge Core API** (`api.curseforge.com`), which needs an API key (free, but operator must register at console.curseforge.com and hand it over).

**Tooling alternatives evaluated:**

| Tool | Verdict |
|---|---|
| `packwiz` | Author-side tool. Builds packs from a `pack.toml`, doesn't fetch arbitrary CF server packs. **Not useful here.** |
| `ftb-app-cli` | Only fetches FTB-app packs (Feed The Beast). None of our 6 are FTB packs. **Not useful.** |
| `mrpack-install` / Modrinth CLI | Works great — but only for `.mrpack` files on Modrinth. **Of our 6 packs, 0 are Modrinth-native.** |
| `curseforge-downloader` / `ferium` | Both use the CF Core API → still need the API key. |
| Direct `edge.forgecdn.net` URL if operator pastes it | **Works, no auth, no CF challenge.** This is the lowest-friction path. |

**Recommendation:** operator either (a) registers a CurseForge API key (5 min, free), or (b) opens each pack page in their browser, right-clicks the "Server Pack" download button, and pastes the 6 direct CDN URLs into the runbook. Option (b) is faster for a one-shot test campaign.

---

## 2. Server-pack typical size (BMC5 reference)

BMC5 (Better MC 5, neoforge 1.21.1, ~350 mods) public listings show:

- **Compressed server zip:** ~600–900 MB (mods + configs + scripts; no client resources/shaders).
- **Unpacked, pre-first-boot:** ~1.5–2.5 GB.
- **After first boot (world gen + libraries + JEI cache + neoforge runtime):** ~3–5 GB.

For ~200-mod packs (Stone Block 4, Protocol Zero) expect 60–70 % of those figures. For 1.20.1 forge packs (Linggango, OtherWorld) similar. For 1.18.2 (Isle of Berk) smaller — ~300 MB zip, ~2 GB on disk after boot.

**Worst-case envelope per pack: 6 GB on disk during a test.**

---

## 3. Disk budget

Measured: `/dev/sda1 91 GB free` (current). Headroom rule: keep 30 GB for OS/Venary/Postgres/logs → **61 GB working budget**.

Sequential testing (`/tmp/mc-test-<slug>` created, used, deleted between packs) means peak usage = 1 pack at a time → ~6 GB. **Massive margin.** Even if a pack misbehaves and balloons to 15 GB, we still have 4× headroom.

✅ Disk is a non-issue.

---

## 4. RAM budget

Measured: `free -h` → `total 23 Gi, available 15 Gi`. Venary needs 8 GB reserved → **7 GB ceiling for one MC server.**

Modern modded servers want 6–10 GB. 6 GB is the sane floor for 1.21.1/1.20.1 packs; 1.18.2 can run comfortably at 4 GB.

**Failure mode:** Java `-Xmx6G` is a **commitment**, not a ceiling on RSS. With Metaspace + direct buffers + Netty pools + native libs (LWJGL is excluded server-side, but rubidium/sodium variants aren't loaded either), RSS commonly lands at `Xmx + 1.5 GB ≈ 7.5 GB`. That collides with Venary's 8 GB reserve.

**Mitigation (required):** instead of `-Xmx6G` use `-XX:MaxRAMPercentage=40` (≈ 9 GB of 23 GB total, but the JVM self-tunes down under pressure) **or** the safer fixed pair:

```
-Xms2G -Xmx5G -XX:MaxMetaspaceSize=512M -XX:MaxDirectMemorySize=1G
```

That caps total RSS near 6.5 GB and keeps Venary alive. Document any pack that fails to boot under 5 GB heap and skip rather than push to 6.

⚠️ RAM is tight but workable — **use the capped flags above, not `-Xmx6G`.**

---

## 5. Per-pack runbook (recipe — do not execute in recon)

Generic 6-step template, per pack:

```bash
SLUG=<slug>; MCVER=<mcver>; LOADER=<loader>; JAR=VonixServerUtilities-${MCVER}-${LOADER}.jar
mkdir -p /tmp/mc-test-$SLUG && cd /tmp/mc-test-$SLUG
curl -L -o server.zip "<DIRECT_CDN_URL>"          # from operator or CF API
unzip -q server.zip
cp /root/DEV/Vonix-Server-Utilities-Architechtury/out/$JAR mods/
echo "eula=true" > eula.txt
# launch via tool, NOT shell:
#   terminal(command="java -Xms2G -Xmx5G -XX:MaxMetaspaceSize=512M -XX:MaxDirectMemorySize=1G -jar <server-launcher>.jar nogui",
#            background=true, notify_on_complete=true, workdir="/tmp/mc-test-$SLUG")
# then: search_files for "Done (" in logs/latest.log; process.write("/vonixsu version\n"); process.write("/stop\n")
# capture: tail -n 200 logs/latest.log > /tmp/mc-test-$SLUG.report.txt
rm -rf /tmp/mc-test-$SLUG
```

Per-pack matrix:

| # | Pack | MC | Loader | VonixSU jar | Est. boot | Watch-for |
|---|---|---|---|---|---|---|
| 1 | BMC5 v50 | 1.21.1 | neoforge 21.1.x | `1.21.1-neoforge` | 90–180 s | mixin apply errors, `duplicate modid`, missing `connector` for fabric-bridged mods |
| 2 | Stone Block 4 v1.15.3 | 1.21.1 | neoforge 21.1.x | `1.21.1-neoforge` | 60–120 s | KubeJS script errors (non-fatal), Mekanism config drift |
| 3 | Protocol Zero v1.9.1 | 1.21.1 | neoforge 21.1.x | `1.21.1-neoforge` | 45–90 s | GeckoLib mixin conflicts, Embeddium/Oculus server-side stubs |
| 4 | Linggango v6.4.5.7 | 1.20.1 | forge 47.x | `1.20.1-forge` | 60–120 s | Create + Create-addon load order, `MixinTransformerError` |
| 5 | OtherWorld DND v8 HF2 | 1.20.1 | forge 47.x | `1.20.1-forge` | 90–180 s | Origins/Pehkui hash mismatch, Iris/Sodium server stubs |
| 6 | Isle of Berk v3.1.7 | 1.18.2 | forge 40.x | `1.18.2-forge` | 30–60 s | Ice & Fire datapack reload OOM at 4G; legacy Forge access-transformer warnings |

For all six, the pass criterion is **`/vonixsu version` returns a build string matching the jar we dropped in**. Anything else (boot failures, crashes, mixin refusal) is logged and the pack is marked failed-but-not-blocking.

---

## 6. Failure paths & abort criteria

| Scenario | Detection | Mitigation |
|---|---|---|
| CF download requires API key | `curl -L` returns 403/CF challenge or HTML | Ask operator for API key OR direct CDN URLs; do **not** try to solve Cloudflare. |
| Server-pack loader version ≠ VonixSU build target (e.g. neoforge 21.1.**73** vs we built against 21.1.**40**) | Boot log: `Mod X requires neoforge [21.1.40,21.1.50)` | Skip pack, record in report; do not rebuild jar mid-campaign. |
| Server boot > 5 min | `notify_on_complete` hasn't fired + no `Done (` in log after 300 s | `process.kill`, grab last 200 lines, mark `TIMEOUT`, continue. |
| Server OOM-killed | exit code 137 OR `OutOfMemoryError` in log | Already capped via `-Xmx5G`; Venary survives. Mark pack `OOM`, continue. |
| Pack ships its own start script with hardcoded `-Xmx10G` | Inspect `run.sh` / `user_jvm_args.txt` before launch | Edit `user_jvm_args.txt` to our capped flags **before** first launch. |
| Disk fills mid-test | `df` pre-check shows < 10 GB free | Abort campaign, demand operator clear space. |

**Hard abort:** Venary process disappears from `ps` → kill MC server immediately, do not start next pack, report to parent.

---

## 7. Verdict

🟡 **YELLOW — feasible with one operator action.**

What works headlessly today:
- Disk budget (huge margin).
- RAM budget *with the capped JVM flags*, not raw `-Xmx6G`.
- Direct downloads from `edge.forgecdn.net` once URLs are known.
- The 6-step runbook itself.

What blocks fully-autonomous execution:
- **CurseForge web pages are Cloudflare-challenged.** We cannot discover server-pack download URLs headlessly. Operator must either:
  1. Paste 6 direct `edge.forgecdn.net` URLs into the wave-7 task, **OR**
  2. Provide a CurseForge Core API key (`x-api-key` header) so the wave-7 agent can call `GET /v1/mods/{modId}/files` and pick the server-pack file.

Either unblocks the entire campaign. No need to fall back to running on the operator workstation.

**Recommendation to parent:** before dispatching wave-7, request the 6 CDN URLs from the operator (faster than API-key registration). Wave-7 then executes this runbook verbatim, sequentially, with the capped JVM flags. Expected wall time: ~30–60 min for all 6 packs.
