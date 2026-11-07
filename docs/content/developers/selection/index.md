---
title: Selection file
description: The file that keeps which extensions an administrator turned off.
---

An administrator turns an extension off without removing its jar. Chassis keeps that choice in the file `HostOptions.selectionFile` names, so the extension stays off across restarts:

```yaml
# The modules an administrator turned off; their jars stay in the folder.
disabled:
  - shop
```

## How the file changes

- `Extensions.disable(id)` adds the id and `Extensions.enable(id)` removes it. Chassis writes the file only when the list changes, replacing it at once so a crash never leaves half a file.
- A missing file means nothing is turned off. A host whose administrators never turn anything off never gets one.
- `ChassisHost.start()` reads the file. A listed extension is installed as `DISABLED`, and the extensions that depend on it wait with a reason such as `'shop' ^1 (disabled)`.

An administrator may edit the file while the host is stopped. While the host runs, turn extensions on and off through the [control API](../control/index.md): `rescan()` applies the file only to jars it installs for the first time.

## When the file cannot be read or written

| Where | What happens |
| --- | --- |
| `start()` | Throws `IOException` with `<file> is not valid YAML: ...`; no extension starts. |
| `rescan()` | Logs `Could not look at <folder> again` and keeps the extensions it already knows. |
| `enable` or `disable` | Throws `ExtensionException` with `Could not keep the choice for '<id>' in <file>: ...` and changes nothing. |

## Reading the file yourself

`SelectionFile` reads and writes the same file, for a host that shows or edits the choice outside Chassis:

```java
SelectionFile selection = new SelectionFile(dataFolder.resolve("modules.yml"), "module");
Set<String> disabled = selection.disabled();
```

The second argument is the host's kind word, which the file's comment uses. Change the file this way only while the host is stopped; a running `ChassisHost` writes it on every `enable` and `disable`.
