# VSU 2.0.0 common-generation repository

This repository is the single source tree for the Vonix Server Utilities common-generation line. The common line starts at **2.0.0** and is published as the prerelease label **`2.0.0-common.1`**.

`2.0.0-common.1` identifies the repository/layout generation. It does **not** rename the embedded mod version in the existing project lanes: the accepted source currently builds as VSU **1.7.1**, while the Minecraft 26.1.2 artifact retains its target-specific candidate suffix. Existing historical releases remain immutable.

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

- GitHub release label: **`2.0.0-common.1`** (prerelease).
- Embedded project version: **1.7.1** for the established lanes.
- Static evidence: the accepted candidate passed the parent build/package matrix and source/artifact parity checks for the requested lanes.
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

The common-generation label is kept separate from embedded project SemVer so historical `v1.7.1` artifacts and metadata remain truthful. A stable major-version bump requires a separate public API, configuration, persistence, network, and migration compatibility review; this prerelease must not be described as a stable replacement release.
