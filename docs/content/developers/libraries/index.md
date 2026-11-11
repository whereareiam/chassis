---
title: Libraries
description: Load the libraries an extension declares into its own class loader, with Attache or a loader of the host's own.
---

An extension that needs a library may shade it into its jar, or declare it and let the host load it. A host that loads libraries passes a `LibraryLoader` in `HostOptions.libraries`; Chassis calls it for each extension once its class loader exists and before its main class loads. Without one, `LibraryLoader.NONE` loads nothing.

The libraries land in the extension's own class loader, next to its jar:

- the extension sees them after the packages the host exports, so a library the host exports is the host's;
- other extensions see them only through the [packages the extension exports](../class-loading/index.md);
- they go with the extension's class loader when it stops.

## Attache

`chassis-adapter-attache` loads the libraries an extension declares with [Attache](https://github.com/whereareiam/attache)'s Gradle plugin. The host ships Attache's standalone library manager and hands its own manager to the loader, whose repositories, log, verbosity and download folder every extension shares:

```kotlin
dependencies {
    implementation("me.whereareiam.chassis:chassis-adapter-attache:0.1.0")
    implementation("me.whereareiam:attache-standalone:1.3.0")
}
```

```java
import me.whereareiam.chassis.adapter.attache.AttacheLibraryLoader;

HostOptions options = HostOptions.builder()
        // ...
        .libraries(new AttacheLibraryLoader(hostLibraries))
        .build();
```

`hostLibraries` stands for the host's own `LibraryManager`. Each extension gets a manager of its own that reads only the descriptors in the extension's jar and adds the libraries to the extension's class loader. A library one of the host's class loaders already provides is skipped.

An extension declares its libraries in its build:

```kotlin
plugins {
    id("me.whereareiam.attache") version "1.3.0"
}

dependencies {
    attache("org.jdbi:jdbi3-core:3.49.5")
}

attache {
    // Only the configured database's driver is downloaded.
    group("sqlite") {
        library("org.xerial:sqlite-jdbc:3.50.3.0")
    }
    group("postgres") {
        library("org.postgresql:postgresql:42.7.8")
    }
}
```

The libraries outside a group are loaded before the main class. When one cannot be downloaded or loaded, the extension is `FAILED` with `'<id>' could not load its libraries: <why>`.

## Groups

A group holds libraries only some installations need. The extension loads it through its context once it knows it needs it:

```java
@Override
public void enable(@NotNull ExtensionContext context) {
    context.libraries().loadGroups(settings.database().toLowerCase(Locale.ROOT));
}
```

`loadGroups` throws `IllegalArgumentException` when the extension declares no group of a name, and `IllegalStateException` when a library cannot be loaded. A group already loaded is skipped, so asking twice does no harm. Under `LibraryLoader.NONE` an extension has no groups.

## A loader of the host's own

A host that loads libraries by other means implements `LibraryLoader`. It gets a `LibraryTarget`: the extension's descriptor, its jar and its class loader, `add(path)` to add a library jar to that loader, and `onClose(resource)` for what must be closed when the extension stops:

```java
LibraryLoader loader = target -> {
    for (Path library : librariesOf(target.descriptor().getId()))
        target.add(library);

    return LibraryLoader.NONE.load(target);
};
```

`librariesOf` stands for the host's own lookup. Chassis closes what the loader handed to `onClose` after the extension's own resources and before its class loader, also when the extension fails to start. The extension's class loader has a public `addPath(Path)`, as `add` does, for library managers that look for such a method.
