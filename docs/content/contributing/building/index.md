---
title: Building Chassis
description: Build and test Chassis, and try a local build in a host.
---

Run from the repository root. Sources are compiled and tested with Java 21 through a Gradle toolchain, whichever Java starts Gradle:

```shell
./gradlew test
```

`test` runs the unit tests of every module. The pull request checks run the same tests and build every artifact:

```shell
./gradlew test build
```

`build` also writes each module's jar, sources jar and Javadoc jar under the module's `build/libs/`.

The first build downloads Gradle's plugins and the dependencies, including NeoForge's loader for `platform-neoforge`, from Maven Central, the Gradle plugin portal, NeoForge's repositories and `https://registry.whereareiam.me/maven/packages`.

## Versions

The version of every artifact comes from the `VERSION` environment variable and defaults to `dev`:

```shell
VERSION=0.1.0-local ./gradlew build
```

## Trying a local build in a host

Publish the artifacts to the local Maven repository:

```shell
VERSION=0.1.0-local ./gradlew publishToMavenLocal
```

A host that lists `mavenLocal()` among its repositories then depends on `me.whereareiam.chassis:chassis-common:0.1.0-local`. Chassis's own build reads `mavenLocal()` first as well, so a local build of one of its libraries reaches it the same way.

## Releases

A published GitHub release verifies the tagged commit and publishes the artifacts to `https://registry.whereareiam.me/maven/packages`. A development build is started by hand from the Actions tab and publishes a version named after the branch and commit, such as `dev-1a2b3c4`. See `AGENTS.md` in the repository root for release notes and labels.

See [Modules](../modules/index.md) for how the repository is laid out and [Testing a change](../testing/index.md) for where tests go.
