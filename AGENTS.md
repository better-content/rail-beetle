# AGENTS.md

## Project identity

- Repository and artifact: `rail-scout`
- Mod ID and namespace: `rail_scout`
- Base package: `com.bettercontent.railscout`
- Java: 17
- Minecraft/Forge: 1.20.1 / 47.4.13
- Optional runtime integration: Create 6.0.8

This is a clean implementation. Do not import, inspect, migrate, or add compatibility
for any earlier Rail Crawler implementation or artifact.

## Validation

- Run `./gradlew verifyFast` for deterministic tests and a staged runtime JAR.
- Run `./gradlew verifyFull` for Forge GameTests with Create, Alex's Caves, and
  YUNG's Better Caves loaded.
- Do not commit generated Gradle state, build output, runtime worlds, logs, or IDE files.

## Commit discipline

Commit coherent changes only after required validation passes. Push only when a
canonical remote exists.
