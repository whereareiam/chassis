---
title: Commands
description: Give administrators the commands that list, turn on, turn off and reload a host's extensions.
---

`chassis-adapter-commandant` mounts the commands that control a host's extensions under the host's root command,
with its kind word. For Arbor, whose root is `arbor` and whose word is `module`:

| Command | What it does |
| --- | --- |
| `/arbor module list` | Every installed extension with its version and state, and why one waits or failed |
| `/arbor module info <id>` | Its name, version, state, description, authors and jar |
| `/arbor module enable <id>` | Turns it on, kept in the selection file, and starts it with what was waiting for it |
| `/arbor module disable <id>` | Turns it off; the extensions that depend on it wait |
| `/arbor module reload <id>` | Loads it again from its jar, such as after the jar was replaced |
| `/arbor module rescan` | Installs the jars added since and starts what can start |

## Dependencies

The adapter builds on [Cloud](https://cloud.incendo.org) 2 and names each command for
[Commandant](https://github.com/whereareiam/commandant) 2; the host brings Cloud, Commandant's API and Adventure (4.25
or later, or 5), as its platform has them:

```kotlin
dependencies {
    implementation("me.whereareiam.chassis:chassis-adapter-commandant:<version>")
}
```

## Registering the commands

The host gives its Cloud command manager, its extensions, its root and its word, and says how a sender's permission
is checked and how an answer reaches the sender:

```java
ExtensionCommands.builder(manager, () -> host, "arbor", "module")
        .permissions((sender, permission) -> sender.hasPermission(permission))
        .replies((sender, message) -> sender.sendMessage(message))
        .keys("arbor.module.")
        .arguments(TextArgument::of)
        .changes(scheduler.async())
        .build()
        .register();
```

- **Permissions:** each command needs `<root>.command.<kind>.<name>`, such as `arbor.command.module.disable`. The
  host checks it itself (a predicate permission of Cloud), so it works whatever the platform's permission system is.
- **Changes** run on the executor the host gives, since starting or stopping an extension can take a while; the
  answer is sent from there. List and info answer at once.
- **Extensions** are asked at every command: the commands may be registered before the host starts its extensions,
  as platforms that register commands early need.

## Translating the answers

Each answer is a translatable component: the key is the host's prefix followed by the message, such as
`arbor.module.list.header`, and its values are named arguments. The host's translator, registered in Adventure's
`GlobalTranslator`, words it in each sender's language.

- **Keys:** `keys(...)` gives the prefix, ending with a dot; it is `<root>.<kind>.` when not set.
- **Arguments:** `arguments(...)` makes a named argument from a name and a value, such as
  [Semantica](https://github.com/whereareiam/semantica)'s `TextArgument::of`. Without it, the values are arguments by
  position, in the order of the table below.

| Message | Arguments | Sent |
| --- | --- | --- |
| `list.header` | `count` | above the list |
| `list.line` | `id`, `version`, `state`, `reason` | for each installed extension |
| `list.footer` | | below the list |
| `list.empty` | | instead of the list, when none is installed |
| `info` | `id`, `version`, `state`, `reason`, `name`, `description`, `authors`, `jar` | for `info` |
| `enabled`, `disabled`, `reloaded` | `id`, `version`, `state`, `reason` | after the change |
| `rescanned` | `count` | after `rescan` |
| `unknown` | `id` | when no extension has the id |
| `refused` | `id`, `why` | when a change could not be made |
| `state.enabled`, `state.disabled`, `state.waiting`, `state.failed` | | as the value of `state` |
| `reason` | `why` | as the value of `reason` of a waiting or failed extension; empty otherwise |
| `description.list`, `description.info`, `description.enable`, `description.disable`, `description.reload`, `description.rescan` | | as each command's description, which Commandant's help shows |

`count` is a number, so a translation may choose a plural form by it. `state` and `reason` are translatable
components themselves, so they are translated with the answer. `why` is what an extension waits for, why it failed,
or why a change was refused, as Chassis words it.

### Writing the messages

Chassis ships no wording: the host writes these messages in its own translations, in its own style, so the answers
read like the rest of the host's texts. With Semantica, for Arbor's `en/arbor.yml` and the prefix `arbor.module.`:

```yaml
module:
  list:
    header:
      - " "
      - " <green><bold>Arbor</bold>"
      - " "
      - "  <white>Installed modules: <gray>{count}</gray></white>"
    line: "   <dark_gray>▪</dark_gray> <white>{id}</white> <gray>{version}</gray> {state}{reason}"
    footer: " "
    empty: "{@arbor.common.prefix}<white>No module is installed.</white>"
  rescanned: "{@arbor.common.prefix}<white>{count, plural, one {# module is} other {# modules are}} installed.</white>"
  state:
    enabled: "<green>running</green>"
    waiting: "<yellow>waiting</yellow>"
  reason: "<gray>: {why}</gray>"
  description:
    list: "Lists every installed module and its state"
```

A message the translations lack shows as its key, so a host adds every message of the table.

## Naming the commands

Each command carries Commandant's definition id `<kind>-<name>` (`module-list`), by which a host's Commandant
definitions alias, describe or hide it.
