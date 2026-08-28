# AGENTS.md — Vonix Server Utilities

## Repository identity

- **Repository:** `Vonix-Network/Vonix-Server-Utilities-Architechtury`
- **Canonical checkout for this candidate:** `/root/work/mod-v2-common-migration-20260825/candidates-r14/vsu`
- **Default branch:** `master`
- **Project release line:** `2.0.0`
- **License posture:** All Rights Reserved (Vonix Network)
- **Project role:** server-side Minecraft essentials, moderation, administration, and Venary integration

This is one repository containing every supported Minecraft/loader lane. Do not split a version lane into a separate repository or treat a generated build directory as a source checkout.

## Read first

1. `AGENTS.md` (this file)
2. `README.md`
3. `CHANGELOG.md`
4. Root `gradle.properties` and the selected lane's `gradle.properties`
5. The selected lane's `settings.gradle` and `build.gradle`
6. The relevant `docs/` page and lane `CHANGELOG.md`
7. Tests and source before changing behavior

Existing nested `AGENTS.md` files describe individual generated lanes. This root file controls repository-wide scope, release identity, safety, and CI expectations; reconcile nested guidance with the actual files.

## Supported repository layout

| Minecraft | Loaders | Source directory |
|---|---|---|
| 1.18.2 | Fabric, Forge | `vonix_server_utils-1.18.2-fabric-forge-template/` |
| 1.19.2 | Fabric, Forge | `vonix_server_utils-1.19.2-fabric-forge-template/` |
| 1.20.1 | Fabric, Forge | `vonix_server_utils-1.20.1-fabric-forge-template/` |
| 1.21.1 | Fabric, NeoForge | `vonix_server_utils-1.21.1-fabric-neoforgetemplate/` |
| 26.1.2 | NeoForge | `vonix_server_utils-26.1.2-neoforge-template/` |

The first four Architectury lanes have `common/`, `fabric/`, and either `forge/` or `neoforge/`. The 26.1.2 lane is a standalone NeoForge/ModDevGradle project. The repository-level `core/` project contains shared contracts used by the 1.21.1 and 26.1.2 arrangements where the lane settings explicitly include it.

## Version contract

- Every VSU lane uses the same embedded release version: **`2.0.0`**.
- The release tag and GitHub release title are **`v2.0.0`**.
- Do not add Minecraft or loader suffixes to the embedded public version. Target/loader identity belongs in the artifact filename and release matrix.
- Historical versions such as `1.7.1` and older tags remain historical. Never rewrite or force-update immutable tags.
- Keep `gradle.properties`, public identity constants, generated `fabric.mod.json`/NeoForge metadata, tests, README, and changelog aligned.

## Build and CI

The authoritative release build is `.github/workflows/release.yml`.

- A `workflow_dispatch` run is **build-only** because the release job is guarded to tag refs.
- A push of `v2.0.0` runs the complete nine-lane matrix and publishes the GitHub release only after all matrix jobs pass.
- Older Loom lanes run Gradle under Java 21 even when their Java source target is 17. The 1.21.1 lane uses Java 21/Gradle 8.14. The 26.1.2 lane uses Java 25/Gradle 9.2.0.
- CI provisions Gradle explicitly with `gradle/actions/setup-gradle`; do not assume a checked-in `gradle-wrapper.jar` is present.
- The release job must select exactly one non-source/non-dev JAR per supported lane and write `SHA256SUMS`.
- CI does not deploy, activate, restart, or modify a live Minecraft server.

For a selected lane, the normal local command is the installed/provisioned Gradle equivalent of:

```text
gradle build --no-daemon
```

Use the lane's declared toolchain. Do not run a broad multi-lane build from the repository root unless the root build files explicitly support that task.

## Verification expectations

Before calling a candidate release-ready:

- Verify the exact Git commit/tree and clean status.
- Verify all nine release cells are represented.
- Verify archive validity and embedded mod ID/version in every selected JAR.
- Verify loader metadata, dependency ranges, server/client side, and no unexpected third-party source.
- Run the lane's deterministic tests and custom `JavaExec` contract checks where defined.
- Keep static source/build evidence separate from live server activation evidence. This project has no live activation proof from CI alone.
- Review the exact release artifact remotely after publication; compare the remote asset digest with CI's `SHA256SUMS`.

## Shared-code and loader rules

- Keep loader-independent behavior in the lane's `common/` tree.
- Keep Fabric, Forge, and NeoForge APIs in their loader source sets.
- If a lane depends on repository-level `core/`, include the project in that lane's `settings.gradle` and use the explicit dependency already established by the source contract.
- Preserve the typed Architectury event adapter pattern: register event-interface lambdas that forward to common callbacks; never pass a raw `Consumer` where Architectury expects `stateChanged`, `tick`, `join`, or `quit`.
- Any change to common code must be checked against every supported lane, not only 26.1.2.

## Security and protected data

- Never read, print, commit, or transmit bot tokens, API keys, passwords, JDBC URLs, webhook URLs, private keys, or authorization headers.
- Documentation uses placeholders only.
- Do not connect to Venary, Discord, LuckPerms, a live database, or a live server during a source/build gate unless a separate task explicitly authorizes a read-only probe.
- Do not add secrets to GitHub Actions. Secret configuration is an owner-managed operation outside this repository candidate.

## Git and change discipline

- Start with `git status --short --branch`.
- Preserve unrelated changes and nested project ownership.
- Use explicit pathspecs; do not stage Gradle caches, `build/`, runtime worlds, logs, downloaded dependency trees, or candidate evidence.
- Run `git diff --check` before commit.
- Never force-push, rewrite historical tags, or publish a dirty tree.
- A source push is not acceptance; a green CI run is not live runtime proof; a GitHub release is not server deployment.

## Release procedure

1. Inspect remote branch, tags, and releases.
2. Confirm the final source commit and `2.0.0` metadata.
3. Push the exact default-branch commit without force.
4. Run build-only CI and require all nine cells to pass.
5. Push only the exact `v2.0.0` tag.
6. Verify the tag-triggered release, nine assets, release notes, and `SHA256SUMS` through GitHub read-back.
7. Independently inspect the remote commit/tree and release assets.
8. Record any unavailable runtime evidence; do not claim activation.

## Stop conditions

Stop and report instead of guessing on:

- branch/tag/repository identity drift;
- dirty or mixed-scope checkout;
- missing toolchain or unavailable CI evidence;
- any failed lane or artifact metadata mismatch;
- credential/protected-data exposure;
- request to deploy, activate, restart, or alter a live server;
- request to overwrite an immutable release/tag.

## Completion checklist

- [ ] Correct repository and default branch verified.
- [ ] All five Minecraft version directories retained in this repository.
- [ ] All nine VSU loader cells use version `2.0.0`.
- [ ] Root README, docs, changelog, and this AGENTS file agree.
- [ ] CI build-only matrix passes 9/9.
- [ ] Tag-triggered release assets and hashes read back remotely.
- [ ] No live deployment or activation was claimed without evidence.
