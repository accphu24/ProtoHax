# CLAUDE.md

Guidance for Claude Code and other agentic coding tools working in this repository.

## Project Overview

ProtoHax is a Gradle-based Kotlin/JVM library for a platform-agnostic Minecraft: Bedrock Edition relay/packet-layer client. The repository is no longer actively maintained upstream, so prefer small, targeted changes over broad rewrites.

Primary package: `dev.sora.relay`

## Build And Verification

- Use JDK 11. The Gradle build config sets `kotlin.jvmToolchain(11)`.
- Build the project with `./gradlew build`.
- Publish locally with `./gradlew publishToMavenLocal`.
- Create the shaded artifact with `./gradlew shadowJar`.
- There is no conventional unit test suite in this repo. `src/test` is example/runtime code and may require Microsoft/Xbox authentication state; do not assume it can run unattended.

## Repository Layout

- `src/main/java/dev/sora/relay/session`: relay session and listener pipeline.
- `src/main/java/dev/sora/relay/game`: game session state, entities, inventory, world, registries, and events.
- `src/main/java/dev/sora/relay/cheat`: modules, commands, config, and value system.
- `src/main/java/org/cloudburstmc/protocol`: local protocol utility/codec overrides.
- `src/main/resources/assets`: bundled assets and mapping data.
- `src/test`: example relay entry point and supporting test utilities.

## Coding Conventions

- Follow `.editorconfig`: LF endings, UTF-8, final newline, and tabs for Kotlin indentation.
- Keep Kotlin changes consistent with the surrounding code style, including existing package layout and concise module classes.
- Prefer adding behavior through the existing event system (`GameEvent`, `EventManager`, `CheatModule.handle*`) instead of introducing parallel callback mechanisms.
- Register new cheat modules in `ModuleManager.init()` and place them under the appropriate `impl/<category>` package.
- Use the existing `Value` classes for configurable module settings so config serialization and visibility handling keep working.
- Keep protocol, registry, and packet handling changes version-aware. This project is intended to adapt across Bedrock protocol versions.

## Dependency Notes

- Dependencies are declared in `build.gradle`.
- The build uses `mavenLocal()`, Maven Central, OpenCollab repositories, and `mccheatz.github.io/maven_repo`.
- The `embed` configuration feeds the Shadow plugin. If adding a runtime dependency that must be bundled, use `embed`; otherwise use the narrowest suitable Gradle configuration.
- `src/main/resources/assets/mcpedata` is a git submodule. If mapping files are missing, initialize submodules rather than replacing generated data by hand.

## Safety Notes

- Do not commit secrets, OAuth tokens, session data, or local config files. The example auth flow in `src/test/README.md` uses a `.ms_refresh_token` file that must remain local.
- Avoid changing public package names, artifact coordinates, or shaded dependency behavior unless the requested task explicitly requires it.
- Keep documentation in English, matching the repository README guidance.
