---
title: Descriptor
description: The YAML file every extension jar carries, and how Chassis decides from it whether the extension can start.
---

Every extension jar carries a descriptor at the path the host names in `HostOptions.descriptorFile`, such as `example-module.yml` at the root of the jar. Chassis reads it before it loads any class of the jar.

```yaml
id: monitoring
name: Monitoring
version: 1.0.0
main: com.example.monitoring.MonitoringModule
description: Registers the server and reports it.
authors: [whereareiam]
host: ^1
requires:
  gateway: ^1
dependencies:
  - id: storage
    version: ^2
  - id: metrics
    optional: true
exports: [com.example.monitoring.api]
platforms:
  - name: neoforge
  - name: paper
    version: ">=1.21 <1.22 || ~26.1"
plugins: [luckperms]
```

Only `id`, `version` and `main` are required.

## Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | yes | Lowercase letters, digits and hyphens, starting with a letter, at most 64 characters. Unique within a host; it names the extension's data folder, its logger and its entry in the selection file. |
| `name` | no | The name for people. Defaults to the id. |
| `version` | yes | The extension's version, see [Versions](#versions). |
| `main` | yes | The binary name of the main class, which must implement the host's kind of extension. |
| `description` | no | A sentence for people. |
| `authors` | no | A list of names. |
| `host` | no | The range of the host's API versions the extension is built for, checked against `HostOptions.hostVersion`. Defaults to any version. |
| `requires` | no | What the extension needs the host to offer, by the names the host gives them, each with a version range, checked against `HostOptions.offer(...)`. |
| `dependencies` | no | Other extensions of the same host, each with `id`, an optional `version` range (default any) and `optional` (default `false`). |
| `exports` | no | The packages of the jar that the extensions depending on it see, each with its subpackages. Defaults to none: dependents then see no class of the extension. See [What extensions see](../class-loading/index.md#one-extensions-class-loader). |
| `platforms` | no | The platforms the extension runs on, each with `name`, such as `neoforge`, compared in lowercase with `Platform.name()`, and an optional `version` range of the platform's versions (default any), compared with `Platform.version()`. Empty means every platform. See [Platforms](../platforms/index.md#choosing-platforms-and-versions). |
| `plugins` | no | Plugins or mods of the platform the extension needs, by the ids the platform knows them by. |

In code the descriptor is an `ExtensionDescriptor`: `context.descriptor()` inside an extension, `InstalledExtension.getDescriptor()` from the [control API](../control/index.md).

## How Chassis decides

Each check leads to a state. An extension that does not fit the host fails; an extension that lacks something which may still arrive waits.

| Check | When it does not hold | Example of the reason |
| --- | --- | --- |
| `host` accepts the host's API version | `FAILED` | `It is built for the host's API ^2, and the host's API is 1.2.0` |
| `platforms` is empty or names the platform | `FAILED` | `It runs on paper, not on neoforge` |
| the platform's entry accepts the platform's version | `FAILED` | `It runs on paper ~26.1, not on paper 1.21.4` |
| every `requires` entry is offered in an accepted version | `FAILED` | `It requires 'persistence', which the host does not offer` |
| every plugin is loaded | `WAITING` | `the plugin 'luckperms', which is not loaded` |
| every required dependency is installed in an accepted version and enabled | `WAITING` | `'storage' ^2 (not installed)`, `'storage' ^2, and 1.4.0 is installed` |
| the dependencies form no cycle | `FAILED` for every member | `Its dependencies form a cycle: left -> right -> left` |

A failed extension's reason is `getFailure().getMessage()`; a waiting extension lists its reasons in `getWaitingFor()`. See [Lifecycle and states](../lifecycle/index.md) for what happens next.

### Optional dependencies

An optional dependency changes only the order. When it is installed and can start, it starts first and the extension's class loader sees the packages it exports. When it is missing, turned off or fails, the extension starts without it. A version range on an optional dependency still has to accept the installed version, or the extension waits.

## Versions

A version is `major.minor.patch` with an optional pre-release after `-` and build metadata after `+`, as semantic versioning writes them. Missing parts read as `0` (`2` is `2.0.0`), and a leading `v` is allowed. A pre-release orders before its release; build metadata is not compared.

Ranges are written as npm and Cargo write them:

| Range | Accepts |
| --- | --- |
| `*` | any version |
| `1.2.3` or `=1.2.3` | that version alone |
| `^1.2` | `1.2.0` up to, not including, `2.0.0`; below `1.0.0`, up to the next minor (`^0.3` stops before `0.4.0`) |
| `~1.2` | `1.2.0` up to, not including, `1.3.0` |
| `>=1.0`, `>1.0`, `<2`, `<=2` | one bound |
| `>=1.4 <3` | several bounds separated by spaces, all of which must hold |
| `>=1.16 <1.22 \|\| ~26.1` | alternatives separated by `\|\|`, one of which must hold |

A range accepts a pre-release only when a bound of the alternative that accepts it names a pre-release of the same version: `^1` does not accept `1.5.0-beta.1`, `>=2.0.0-beta.1` accepts `2.0.0-beta.2`.

YAML reads a value that starts with `*` as an alias and one that starts with `>` as folded text, so quote such ranges:

```yaml
host: ">=1.4 <3"
dependencies:
  - id: storage
    version: "*"
```

## Descriptors that cannot be read

A jar whose descriptor is missing, is not valid YAML, lacks a required field or holds a malformed value is installed as `FAILED` under its file name without `.jar`:

| Reason | Example |
| --- | --- |
| no descriptor at the path the host names | `broken.jar has no example-module.yml` |
| a required field is missing | `The descriptor of greeting names no 'main'` |
| the id does not fit the pattern | `The id 'Not_Valid' of x.jar is not lowercase letters, digits and hyphens starting with a letter` |
| an export is not a package name | `The export 'com.example.' of monitoring is not a package name` |
| a version or range is malformed | `The version 'one' of greeting is not a version` |
| a second jar has an id already found | `Both greeting.jar and second-greeting.jar are the extension 'greeting'; second-greeting.jar is left out` |

Jars are read in the order of their file names, so of two jars with one id, the one whose file name sorts first is installed.
