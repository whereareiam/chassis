---
title: Testing a change
description: Where the tests of Chassis live, how they load real extension jars, and which test a change belongs in.
---

Every module tests itself with JUnit under `src/test/java`, and `./gradlew test` runs them all. No test needs Docker, a network or a running game.

## Which test a change belongs in

| Change | Test |
| --- | --- |
| versions and ranges | `chassis-api`: `VersionRangeTest` |
| reading and checking descriptors | `chassis-common`: `DescriptorReaderTest` |
| discovery, resolution, loading, lifecycle, selection, the control API | `chassis-common`: `ChassisHostTest` |
| starting and stopping one extension, undoing a failed start | `chassis-common`: `ExtensionLauncherTest` |
| transforming extension jars and keeping the results | `chassis-common`: `JarTransformationTest` |
| entries and their states | `chassis-common`: `ExtensionEntriesTest` |
| log lines and events | `chassis-common`: `ExtensionEventsTest` |
| services, priorities and watches | `chassis-common`: `ServiceRegistryTest` |
| the Guice adapter | `chassis-adapter-guice`: `GuiceExtensionFactoryTest` |

`platform-neoforge` has no tests; `NeoForgePlatform` needs a running NeoForge. `BukkitPlatformTest` and `VelocityPlatformTest` give their platform a server or proxy made of `java.lang.reflect.Proxy` instances that answer only what the platform asks.

Extend the closest existing test instead of starting a parallel style.

## How the host tests load extensions

`ChassisHostTest` and `GuiceExtensionFactoryTest` start a real `ChassisHost` on a `@TempDir` and write extension jars with `ExtensionJar` from `chassis-testing`, the same way a host's tests do (see [Testing a host](../../developers/testing/index.md)):

- The kind of extension the tests load is a test interface in an exported package: `TestModule` in `me.whereareiam.chassis.common`, `GuiceModule` in `me.whereareiam.chassis.adapter.guice`. The export layer exports `me.whereareiam.chassis` from the test's class loader.
- The extensions themselves live in `org.example.<name>` under the test sources, outside every exported package, so their classes come from the written jar. `org.example.greeting` registers a service and holds a class, `Shared`, that `org.example.dependent` uses through its dependency; `org.example.failing` throws from `enable`.
- Extensions report back through system properties, which the test clears before each run, and through services of a type the host exports, such as `Greeting`.
- The test's `Platform` answers `peer(id)` from a map the test fills, to make an extension wait for a peer and then start on `rescan()`, or to give it a peer's class loader (a `URLClassLoader` over a jar of `org.example.peer`).

A change that holds extension objects anywhere gets a check that a stopped extension is collected: take a `WeakReference` to `host.classLoader(id)`, stop the extension, and assert `LeakCheck.collected(reference, Duration.ofSeconds(10))`. The reload test in `ChassisHostTest` and the Guice test show it.

## What a test asserts

Assert what a host notices, by the public surface: states, `getWaitingFor()` lines and failure messages of `InstalledExtension`, the order of `subscribe` events, services found or gone, and the contents of the selection file. The reason texts are part of what administrators read, so tests compare them exactly.
