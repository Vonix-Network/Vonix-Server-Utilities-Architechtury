# VSU r4b Candidate Report

Status: BLOCKED (verification infrastructure)

Base head: `da19dd16f5f66a0b3f9e610019fa02155e7d5050`
Base tree: `d00a400df44a922174f95c697328320d4e9f6e4b`

Repair: the 1.18.2, 1.19.2, and 1.20.1 common cells now contain no loader or Architectury imports. Shared lifecycle, command, tick, player, death, moderation, playtime, rank, reward, and config-polling behavior is exposed through loader-neutral `PlatformEvents`; Fabric and Forge modules install the matching adapters. The 1.21.1 pilot source was preserved.

Evidence:

- Version-common forbidden-import scan: PASS, zero matches.
- Root-core forbidden-import scan: PASS, zero matches.
- `git diff --check`: PASS.
- Exact pilot command from the 1.21.1 template: `:core:test :core:compileJava :common:compileJava :fabric:build :neoforge:build`.
- Java: OpenJDK `25.0.3`; `GRADLE_USER_HOME=/root/work/mod-v2-common-migration-20260825/.gradle-r4b`.
- Pilot: BLOCKED before Gradle task execution; wrapper reports missing `gradle/wrapper/gradle-wrapper.jar`. Durable log: `/tmp/vsu-r4b-pilot.log`.
- Root core tests/compile: NOT_RUN; no root wrapper and system Gradle unavailable.
- Archives, metadata, resources, mixins, dependencies, hashes: NOT_RUN; no produced archives.
- Secret/forbidden-effect scan: PASS for command/effect patterns; no credentials inspected or emitted.

Guardian remains blocked and untouched. `EFFECTS=NOT_PERFORMED`. This is not acceptance.
