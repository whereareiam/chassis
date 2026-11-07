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
[Commandant](https://github.com/whereareiam/commandant); the host brings Cloud, Commandant's API and Adventure with
MiniMessage, as its platform has them:

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
        .messages(() -> messages.getModules())
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

## Wording the answers

`ExtensionMessages` holds every answer in MiniMessage, with defaults. A host keeps it in its messages file, so an
administrator words them; the tags each one may use are named on its field: `<kind>`, `<id>`, `<version>`,
`<state>`, `<reason>`, `<count>`. Each command carries Commandant's definition id `<kind>-<name>` (`module-list`),
by which a host's Commandant definitions alias, describe or hide it.
