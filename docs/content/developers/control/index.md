---
title: Control API
description: List, enable, disable, reload and rescan a host's extensions while it runs.
---

`Extensions` controls a host's extensions while it runs. `ChassisHost` implements it, so a host hands the same object to whatever administrators use, such as its commands:

```java
Extensions extensions = modules; // the host's ChassisHost
```

Changes are made one at a time, on the caller's thread. None of them touches the host itself or the game content.

## Reading the state

`list()` returns every installed extension, the running ones in the order they started and the others after them; `find(id)` returns one. Each `InstalledExtension` is a snapshot of the moment it was read:

| Getter | Value |
| --- | --- |
| `getId()` | The id from the descriptor, or the jar's file name without `.jar` when the descriptor could not be read. |
| `getDescriptor()` | The descriptor, or `null` when it could not be read. |
| `getJar()` | The path of the jar. |
| `getState()` | `ENABLED`, `DISABLED`, `WAITING` or `FAILED`; see [Lifecycle and states](../lifecycle/index.md). |
| `getWaitingFor()` | One line per missing dependency or plugin; empty unless it waits. |
| `getFailure()` | Why it failed, or `null` unless it failed. |

A listing command could print:

```java
for (InstalledExtension extension : extensions.list()) {
    String line = extension.getId() + " " + extension.getState();
    if (!extension.getWaitingFor().isEmpty())
        line += ", waiting for " + String.join(", ", extension.getWaitingFor());
    if (extension.getFailure() != null)
        line += ": " + extension.getFailure().getMessage();
    sender.sendMessage(line);
}
```

`sender` stands for whatever the host answers a command with.

## Changing the state

Each method takes an id and returns the extension afterwards. It throws `ExtensionException` when no extension of that id is installed; `getExtension()` on the exception names the id.

| Call | Effect |
| --- | --- |
| `enable(id)` | Removes the id from the [selection file](../selection/index.md), then starts the extension when it can and every waiting extension that can start now. A `FAILED` extension is tried again; one whose descriptor could not be read is read from its jar first. |
| `disable(id)` | Adds the id to the selection file, stops the extension's enabled dependents first, then the extension. It becomes `DISABLED`; its dependents wait for it. |
| `reload(id)` | Stops the extension and its enabled dependents, reads its jar again in a fresh class loader, and starts them again. The jar may have been replaced with a new version. A `DISABLED` extension stays off. |
| `rescan()` | Looks at the folder again: installs jars added since, starts what can start now, and reads again the jars of extensions whose descriptor could not be read. It forgets extensions whose jar is gone, unless they run. Running extensions stay as they are. Returns the whole list. |

`enable` on a running extension and `disable` on a turned-off one change nothing but the selection file.

`reload` throws `ExtensionException` in two more cases:

- the jar is gone: the extension is no longer installed;
- the jar now holds another id: the old extension is gone, and the jar is installed and started under the new id.

### Waiting for a plugin

An extension that names a plugin in its descriptor waits until the platform has loaded it. On platforms that load plugins while running, call `rescan()` after a plugin loads, and the waiting extensions start. On NeoForge every mod is loaded before the server starts, so an extension never waits for a mod that comes later.

## Following changes

`subscribe(listener)` follows every change of state from then on, as shown in [Lifecycle and states](../lifecycle/index.md#following-changes). Close the returned `AutoCloseable` to stop.
