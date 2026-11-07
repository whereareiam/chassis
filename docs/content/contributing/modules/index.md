---
title: Modules
description: How the repository is laid out, which module owns what, and how a host's extensions move through Chassis.
---

## Module map

| Module | Directory | Owns |
| --- | --- | --- |
| `chassis-api` | `chassis-api/` | The contracts hosts and extensions compile against: `Extension`, `ExtensionContext`, `ExtensionFactory`, `Extensions`, `Services`, `Platform`, descriptors, versions, states, events and exceptions. No behavior beyond parsing versions and ranges. |
| `chassis-common` | `chassis-common/` | The standard host, `ChassisHost`, and everything it runs: discovery, resolution, loading, lifecycle, services and the selection file. |
| `chassis-adapter-guice` | `chassis-adapter-guice/` | `GuiceExtensionFactory`. Guice is `compileOnly`; the host ships it. |
| `platform-neoforge` | `chassis-platform/type/platform-neoforge/` | `NeoForgePlatform`. NeoForge's loader is `compileOnly`. |
| `chassis-testing` | `chassis-testing/` | `ExtensionJar` and `LeakCheck` for hosts' tests. |

Outside their tests, the other modules depend on `chassis-api` and on no other module of Chassis; `chassis-common` reads YAML through Configura. Every module is published under `me.whereareiam.chassis`.

Not in the repository yet: adapters for shared libraries (`chassis-adapter-attache`) and commands (`chassis-adapter-commandant`), and platforms for Bukkit, Velocity and BungeeCord.

`build-logic/` holds the Gradle conventions. `module-java` sets the group, the Java 21 toolchain, Lombok, JetBrains annotations, JUnit, and sources and Javadoc jars; `module-api` is `module-java` with publication, for `chassis-api`; `packaging-publication` publishes a module to Maven. A published module that is not the API applies `module-java` and `packaging-publication`.

## Inside `chassis-common`

| Package | Class | Role |
| --- | --- | --- |
| `common` | `ChassisHost` | Owns every installed extension and its state, makes every change under one lock, and implements `Extensions`. |
| `common` | `HostOptions` | What the host tells Chassis. |
| `common` | `SelectionFile` | Reads and writes the list of turned-off ids. |
| `common.discovery` | `ExtensionDiscovery`, `DescriptorReader` | List the jars of the folder and read and check each descriptor. A jar that cannot be read becomes a `Found` with a failure, never an exception. |
| `common.resolution` | `DependencyResolver` | Decides, without side effects, which candidates fail, which wait and in which order the rest start. |
| `common.loading` | `ExportLayer`, `ExtensionClassLoader` | The parent loader with the host's exports, and one loader per extension: export layer, own jar, dependencies' jars. |
| `common.lifecycle` | `LoadedContext` | The base context of one load. It keeps what the extension hands to `onClose` and removes the extension's services when closed. |
| `common.service` | `ServiceRegistry` | The services of every owner, with `as(owner)` for an owner's view and `removeOwner(owner)` for its removal. |

## How an extension moves through Chassis

1. `ChassisHost.start()` reads the selection file and asks `ExtensionDiscovery` for every jar. Each becomes an entry: `DISABLED` when listed, `FAILED` when unreadable, undecided otherwise.
2. `settle()` gives the undecided and waiting entries to `DependencyResolver` together with the running ones. The resolver fails what does not fit the host, the platform or the offers, or forms a cycle; it moves to waiting what lacks a plugin or a dependency, until nothing changes; it orders the rest depth first.
3. `settle()` starts the ordered entries one by one. Before each it checks again that every required dependency is enabled, because an earlier start in the same pass may have failed. It repeats until a pass starts nothing.
4. Starting builds the `ExtensionClassLoader`, the `LoadedContext`, the host's context and the instance, then calls `enable`. Any failure closes the context and the loader before the entry becomes `FAILED`.
5. Stopping calls `disable`, closes the `LoadedContext` (resources in reverse order, then `removeOwner`), and closes the loader, whatever `disable` threw. `stopDependents` stops the enabled extensions that depend on one, directly or not, the last started first.

After an extension stops, `ChassisHost` keeps only its descriptor, jar path and state. That, and the registry dropping everything of the owner, is what lets a stopped extension's loader be collected; the tests check it with `LeakCheck`. The one exception is the failure of an extension that failed to start: it may be an exception of the extension's own classes, and it is kept until the extension is enabled or reloaded again.

## Rules that keep it that way

- Contracts go into `chassis-api`, behavior into `chassis-common`. A host-specific idea, such as a kind of extension or a context method, belongs to the host, not to Chassis.
- `chassis-api` is the public surface: every public type and method has complete Javadoc, and a change to it is an `API:` change.
- Adapters and platforms depend only on `chassis-api` and keep their third-party library `compileOnly`, so the host chooses its version.
- Nothing in Chassis keeps a strong reference to an extension's classes beyond its stop. A new place that holds extension objects removes them in the stop path and gets a `LeakCheck` test.
