---
title: Lifecycle and states
description: When an extension starts, waits, fails and stops, and what Chassis removes at each step.
---

Every installed extension is in one of four states, an `ExtensionState`:

| State | Meaning | Leaves it when |
| --- | --- | --- |
| `ENABLED` | It runs. | It is turned off, reloaded, a dependency stops, or the host stops. |
| `WAITING` | It waits for a dependency or a plugin of the platform; `getWaitingFor()` says which. | What it waits for is there: after a dependency starts, `enable`, `rescan` or `reload`. |
| `DISABLED` | An administrator turned it off; it stays off across restarts. See [Selection file](../selection/index.md). | `enable` turns it on. |
| `FAILED` | Its jar or descriptor could not be read, it does not fit the host's API, platform or offers, its dependencies form a cycle, or it failed to start. `getFailure()` says why. | `enable` or `reload`; `rescan` reads a jar again only when its descriptor could not be read. |

## Starting the host

`ChassisHost.start()`:

1. reads the [selection file](../selection/index.md);
2. reads the descriptor of every jar in the folder, in the order of their file names, and installs each; a jar that is turned off is `DISABLED`, a jar that cannot be read is `FAILED`;
3. [decides](../descriptor/index.md#how-chassis-decides) which of the others can start, and starts them so that every dependency starts before the extensions that need it.

If an extension fails to start, the extensions that need it wait, and Chassis decides again until a pass starts nothing more.

`start()` throws `IOException` only when the folder or the selection file cannot be read; a broken jar never stops the others.

## Starting one extension

For each extension it starts, Chassis:

1. makes its class loader over the export layer and the loaders of its enabled dependencies;
2. runs the host's [preparation](../class-loading/index.md#preparing-a-loader) on that loader;
3. loads the main class and checks that it implements the host's kind;
4. creates the data folder `<dataFolder>/<id>`, which is kept across loads;
5. makes the context of this load: the base context, through the host's `ContextFactory`;
6. makes the instance through the host's `ExtensionFactory`;
7. calls `enable(context)`.

The extension is then `ENABLED`, and the host's log shows `Enabled the module <id> <version>`.

If any step throws, Chassis closes what the extension handed to `onClose` so far, removes the services and watches it registered, closes what the preparation returned and then its class loader, and marks it `FAILED` with the exception as its failure. The log shows `The module <id> failed to start` with the stack trace. Extensions that depend on it wait with a reason such as `'failing' * (failed)`.

## Stopping one extension

When an extension stops, Chassis:

1. calls `disable()`;
2. closes what the extension handed to `onClose`, the last first; a resource that fails to close does not keep the others open;
3. removes the services it registered and the watches it holds, and tells the other watchers that its services went;
4. closes what the host's preparation returned for this load;
5. closes its class loader.

Steps 2 to 5 happen even when `disable()` throws; the failure is logged as `The module <id> did not stop cleanly`. After `disable()` the context accepts no more resources: `onClose` throws `IllegalStateException`.

An extension stops only after every enabled extension that depends on it has stopped, directly or through others. Those dependents become `WAITING`, with the stopped extension and the word for why it stopped as their reason, such as `'greeting' ^1 (disabled)`, and start again with it.

## Stopping the host

`ChassisHost.stop()` stops every running extension in the reverse order they started. Each becomes `WAITING` for `the modules' host to start`. The selection file is not touched, so the next `start()` starts the same extensions again.

## Following changes

`subscribe` follows every change of state:

```java
AutoCloseable subscription = modules.subscribe(event -> {
    InstalledExtension extension = event.getExtension();
    System.out.println(extension.getId() + ": " + event.getPrevious() + " -> " + extension.getState());
});
```

`getPrevious()` is `null` when the extension was just installed. A listener runs on the thread that made the change, while the host holds its lock: it may read the host, but must not enable, disable, reload or rescan from there. A listener that throws is logged and the others are still told. Close the subscription to stop following.

## The running instance

`ChassisHost.instance(id)` returns the instance of a running extension's main class, and `classLoader(id)` its class loader, for diagnostics and tests. Once the extension stops, `instance` is empty and `classLoader` returns `null`. Do not keep them: a stored instance or loader keeps the stopped extension in memory. See [What extensions see](../class-loading/index.md#when-an-extension-stops).
