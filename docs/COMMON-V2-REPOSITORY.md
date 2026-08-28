# VSU 2.0.0 common-generation repository

This repository is the single source tree for the Vonix Server Utilities common-generation line. The common line starts at **2.0.0** and is published as the release label **`2.0.0`**.

`2.0.0` is the embedded stable release version for every supported lane and identifies the first common-generation release line beginning at `2.0.0`. Existing historical releases remain immutable.

## One repository, all supported Minecraft lanes

| Minecraft | Loaders | Java | Source directory |
|---|---|---:|---|
| 1.18.2 | Fabric, Forge | 17 | `vonix_server_utils-1.18.2-fabric-forge-template/` |
| 1.19.2 | Fabric, Forge | 17 | `vonix_server_utils-1.19.2-fabric-forge-template/` |
| 1.20.1 | Fabric, Forge | 17 | `vonix_server_utils-1.20.1-fabric-forge-template/` |
| 1.21.1 | Fabric, NeoForge | 21 | `vonix_server_utils-1.21.1-fabric-neoforgetemplate/` |
| 26.1.2 | NeoForge | 25 | `vonix_server_utils-26.1.2-neoforge-template/` |

The root `core/` module contains platform-neutral code and tests. Each target directory contains its own version-specific common source and loader modules. Minecraft versions are intentionally kept together; they are not separate repositories.

## Release status

- GitHub release automation: `.github/workflows/release.yml` runs the nine-lane build matrix on `v*` tags and attaches the resulting jars plus `SHA256SUMS` to a stable release; it does not deploy or activate a server.
- Embedded project version: **`2.0.0`** for every supported lane.
- CI gate: the tag-triggered workflow must provide fresh build/package evidence for this versioned successor; earlier R14 evidence does not cover the metadata/workflow changes.
- Live Minecraft activation, deployment, server restart, and production database access were **not performed** for this source snapshot.
- Do not install the 26.1.2 artifact on an unrelated server without matching the required NeoForge and Java 25 environment.

## Building

Use the version-specific build instructions in the target directory and the root build documentation. Do not run a broad multi-version Gradle invocation when the target documentation calls for a narrow profile; different loader toolchains can conflict when configured together.

Typical examples:

```bash
# Core tests
./gradlew -PbuildProfile=coreonly :core:test

# Version-specific root matrix (where supported)
./gradlew -PbuildProfile=mc1211 :fabric:build :neoforge:build
```

For the 26.1.2 target, use the standalone template, its Java 25 toolchain, and the documented NeoForge/ModDevGradle command.

## Release naming

The stable release is a new common-generation release line. Historical `v1.7.1` artifacts and tags remain available as-is. This source release does not by itself establish runtime compatibility guarantees; use the CI/runtime boundaries above.
