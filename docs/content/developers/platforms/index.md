---
title: Platforms
description: Run a host on NeoForge, on a Bukkit server or on Velocity, and describe any other platform to Chassis.
---

Chassis asks the platform three things: its name and its version, for descriptors' `platforms`, and which peers, its plugins or mods, are loaded, with their versions and class loaders, for descriptors' `peers`. A host passes that as a `Platform` in `HostOptions`.

| Artifact | Platform | Name | Version | A descriptor's `peers` are | Their classes |
| --- | --- | --- | --- | --- | --- |
| `platform-neoforge` | NeoForge | `neoforge` | the game's, such as `1.21.1` | mod ids | in the host's loader; [exported](#exporting-another-mods-api) |
| `platform-bukkit` | Bukkit, Spigot, Paper and Folia | `bukkit`, `spigot`, `paper` or `folia` | the game's, such as `1.21.4` | plugin names | the packages an extension names, from the plugin's loader |
| `platform-velocity` | Velocity 3 and 4 | `velocity` | the proxy's, such as `3.4.0` | plugin ids | the packages an extension names, from the plugin's loader |

Each platform's API is `compileOnly`: the host runs on the platform, which provides it.

## Choosing platforms and versions

A descriptor lists the platforms its extension runs on, each by name, with an optional [range](../descriptor/index.md#versions) of the platform's versions:

```yaml
platforms:
  - name: velocity
  - name: paper
    version: ">=1.16 <1.22 || ~26.1"
  - name: folia
    version: "~26.1"
```

The extension runs on any one of them. The name is exact: a server is one platform, so an extension that lists `paper` does not run on Folia until it lists `folia` as well. See [How Chassis decides](../descriptor/index.md#how-chassis-decides) for the reasons it gives.

## NeoForge

`platform-neoforge` holds `NeoForgePlatform`:

```kotlin
dependencies {
    implementation("me.whereareiam.chassis:chassis-common:0.1.0")
    implementation("me.whereareiam.chassis:platform-neoforge:0.1.0")
}
```

```java
import me.whereareiam.chassis.platform.neoforge.NeoForgePlatform;

HostOptions options = HostOptions.builder()
        // ...
        .platform(new NeoForgePlatform())
        .build();
```

- Its name is `neoforge` and its version is the game's.
- A descriptor's `peers` are mod ids: `{id: luckperms}` waits until NeoForge reports the mod `luckperms` as loaded, and a range is compared with the mod's version.
- NeoForge loads every mod before the server starts, so an extension never waits for a mod that comes later. One that waits for a mod which is not installed keeps waiting until the server restarts with the mod.

### Exporting another mod's API

NeoForge puts the classes of every mod into one class loader, the host mod's own included. A host lets its extensions use another mod's API by naming that API's packages in its export layer, over its own loader:

```java
new ExportLayer(ExampleMod.class.getClassLoader(), List.of(
        "me.whereareiam.chassis",
        "com.example.host.api",
        "net.luckperms.api"
));
```

An extension that uses such an API should name the mod in `peers`, so it waits instead of failing when the mod is missing. A mod brings no class loader of its own, so `packages` in its entry adds no classes.

## Bukkit, Spigot, Paper and Folia

`platform-bukkit` holds `BukkitPlatform`, which the host makes from its server:

```kotlin
dependencies {
    implementation("me.whereareiam.chassis:platform-bukkit:0.1.0")
}
```

```java
import me.whereareiam.chassis.platform.bukkit.BukkitPlatform;

HostOptions options = HostOptions.builder()
        // ...
        .platform(new BukkitPlatform(getServer()))
        .build();
```

- Its name is the kind of server, the most specific of `folia`, `paper`, `spigot` and `bukkit`: a fork of Paper is `paper`, and Folia is only `folia`.
- Its version is the game's, read from the server's Bukkit version: `1.21.4` from `1.21.4-R0.1-SNAPSHOT`.
- A descriptor's `peers` are plugin names, as the server knows them: `{id: LuckPerms, version: ^5}` waits until the server has enabled LuckPerms 5, whose version is the one its `plugin.yml` states.
- An extension sees the [packages it names](../class-loading/index.md#packages-of-peers) of a plugin through the plugin's own class loader; the host needs no `depend` on it.

A plugin the server enables after the host waits like a mod that is missing. Call `rescan()` when the server enables a plugin, and the extensions that waited for it start:

```java
@EventHandler
public void onPluginEnable(PluginEnableEvent event) {
    modules.rescan();
}
```

## Velocity

`platform-velocity` holds `VelocityPlatform`, which the host makes from the proxy Velocity injects into it:

```kotlin
dependencies {
    implementation("me.whereareiam.chassis:platform-velocity:0.1.0")
}
```

```java
import me.whereareiam.chassis.platform.velocity.VelocityPlatform;

HostOptions options = HostOptions.builder()
        // ...
        .platform(new VelocityPlatform(proxyServer))
        .build();
```

- Its name is `velocity`, and its version is the proxy's release: `3.4.0` from `3.4.0-SNAPSHOT (git-1a2b3c4d)`.
- A descriptor's `peers` are plugin ids: `{id: luckperms}` waits until Velocity has loaded the plugin `luckperms`, whose version is the one it states.
- An extension sees the [packages it names](../class-loading/index.md#packages-of-peers) of a plugin through the class loader of the plugin's instance.
- Velocity loads every plugin before the proxy starts, so an extension never waits for a plugin that comes later.

It is built against Velocity 3's API and runs on Velocity 3 and 4.

## Other platforms

A host on another platform, or a host that is no plugin at all, implements `Platform` itself:

```java
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class StandalonePlatform implements Platform {
    @Override
    public @NotNull String name() {
        return "standalone";
    }

    @Override
    public Version version() {
        return Version.parse("1.0.0");
    }

    @Override
    public @NotNull Optional<PlatformPeer> peer(@NotNull String id) {
        return Optional.empty();
    }
}
```

- `name()` returns the platform's name in lowercase.
- `version()` returns the platform's version. It may return `null` when the platform has none; only descriptor entries without a range then accept it. Without an override it returns `null`.
- `peer(id)` returns a peer that is loaded now, such as a plugin or mod, as a `PlatformPeer` with its id, the version it states and the loader of its classes, or empty. Chassis asks it whenever it decides which extensions can start, so an extension that waited for a peer starts on the next `rescan()` after the platform loaded it. A peer whose classes are in the host's own loader has no class loader of its own: leave it `null`.
