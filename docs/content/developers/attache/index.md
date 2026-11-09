---
title: Attache
description: Load the libraries each extension declares through Attache into the extension's own class loader.
---

`chassis-adapter-attache` lets an extension bring libraries it declares instead of shading them. Before the extension's main class loads, it reads the [Attache](https://github.com/whereareiam/attache) descriptor inside the extension's jar, downloads the libraries listed there and adds them to the extension's own class loader.

## Dependencies

The adapter does not bring Attache; the host ships `attache-launcher`, which brings Attache's API and its manager:

```kotlin
dependencies {
    implementation("me.whereareiam.chassis:chassis-common:0.1.0")
    implementation("me.whereareiam.chassis:chassis-adapter-attache:0.1.0")
    implementation("me.whereareiam:attache-launcher:1.3.0")
}
```

## Setting up the host

Pass `AttacheLibraries` as the host's [preparation](../class-loading/index.md#preparing-a-loader):

```java
import me.whereareiam.chassis.adapter.attache.AttacheLibraries;

HostOptions options = HostOptions.builder()
        // ...
        .preparation(AttacheLibraries.builder()
                .folder(dataFolder.resolve("libraries"))
                .build())
        .build();
```

| Option | Meaning |
| --- | --- |
| `folder` | Where downloaded libraries are kept, shared by every extension of the host. Required. |
| `logging` | Attache's `LoggingHelper`. Defaults to the `System.Logger` named `attache`. |
| `verbosity` | How much Attache logs for each extension, an Attache `VerbosityMode`. Defaults to `SUMMARY`. |

## Declaring libraries in an extension

An extension declares its libraries with Attache's Gradle plugin, which writes the descriptor `META-INF/attache/<project>/attache.xml` into its jar. Only that jar's descriptor is read for the extension, never the host's.

Declare only what the host does not export. The [export layer](../class-loading/index.md#the-export-layer) comes first, so a class the host exports always wins over an extension's copy; keep the host's API and its shared libraries `compileOnly` in the extension.

## What happens to the libraries

- A library the extension's loader already sees is skipped, so an extension does not load a second copy of what it already has.
- A library only the extension declares lands in its own loader. The host does not see it, other extensions do not see it, and the extensions that depend on this one do not see it either, whatever the extension [exports](../descriptor/index.md#fields).
- When the extension stops, Chassis closes Attache's manager for it and then its loader; the libraries go with the loader. A reload loads them again from `folder`, without downloading them again.
- When a library cannot be downloaded or loaded, the extension fails to start, with Attache's reason, which names the library, as its failure.
