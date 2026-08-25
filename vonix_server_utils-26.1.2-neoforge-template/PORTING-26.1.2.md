# Minecraft 26.1.2 / NeoForge 26.1.2.93 target

This is a dedicated single-loader NeoForge target for Minecraft 26.1.2. It uses ModDevGradle 2.0.140 and Java 25, matching the 26.1.x toolchain used by Viscord and Vonix Guardian. It is intentionally separate from the Architectury Loom 1.21.1 template.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon clean build
```

The build runs both executable regression probes through `check`:

- `muteStateTest` — hydration, optimistic enforcement, persistence failure, and expiry-safe state checks.
- `crateRulesTest` — percentage, selection, playtime, and identifier checks.

## Candidate status

The source candidate is complete for the requested 26.1.2 NeoForge lane. It has been compiled and packaged as:

```text
build/libs/vonix_server_utilities-1.7.1-26.1.2.93-candidate.jar
```

The candidate is not published or deployed. Publication and production installation remain separate owner-authorized effects.
