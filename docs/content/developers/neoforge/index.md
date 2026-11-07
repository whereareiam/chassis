---
title: NeoForge
description: Run a host on NeoForge, and describe any other platform to Chassis.
---

Chassis asks the platform two things: its name, for descriptors' `platforms`, and whether a plugin or mod is loaded, for descriptors' `plugins`. A host passes that as a `Platform` in `HostOptions`.

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

- Its name is `neoforge`, so a descriptor with `platforms: [neoforge]` runs on it.
- A descriptor's `plugins` are mod ids: `plugins: [luckperms]` waits until NeoForge reports the mod `luckperms` as loaded.
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

An extension that uses such an API should name the mod in `plugins`, so it waits instead of failing when the mod is missing.

## Other platforms

Chassis has no platform for Bukkit, Velocity or BungeeCord yet. A host on another platform, or a host that is no plugin at all, implements `Platform` itself:

```java
import me.whereareiam.chassis.platform.Platform;
import org.jetbrains.annotations.NotNull;

public final class StandalonePlatform implements Platform {
    @Override
    public @NotNull String name() {
        return "standalone";
    }

    @Override
    public boolean hasPlugin(@NotNull String id) {
        return false;
    }
}
```

`name()` returns the platform's name in lowercase. `hasPlugin(id)` tells whether a plugin is loaded now; Chassis asks it whenever it decides which extensions can start, so an extension that waited for a plugin starts on the next `rescan()` after the platform loaded it.
