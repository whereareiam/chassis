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
| `repository` | A repository every extension's libraries may come from, besides those its descriptor names, such as the host's own. Call it once per repository. |
| `transformer` | An Attache `LibraryTransformer` registered on the manager of every extension, which runs on each library it downloads before the library loads. Call it once per transformer, or pass a list to `transformers`. |

A host whose own libraries pass Attache transformers, such as one that downgrades their bytecode for an older Java, hands the same transformers on, so every extension's libraries pass them too:

```java
.preparation(AttacheLibraries.builder()
        .folder(dataFolder.resolve("libraries"))
        .transformers(hostLibraries.getLibraryTransformers())
        .build())
```

Attache keeps their results in `folder`, beside the libraries, and transforms a library again only when it or a transformer's version changes.

## Declaring libraries in an extension

An extension declares its libraries with Attache's Gradle plugin, which writes the descriptor `META-INF/attache/<project>/attache.xml` into its jar. Only that jar's descriptor is read for the extension, never the host's.

Declare only what the host does not export. The [export layer](../class-loading/index.md#the-export-layer) comes first, so a class the host exports always wins over an extension's copy; keep the host's API and its shared libraries `compileOnly` in the extension.

## Groups

A library only some installations need, such as the driver of one database, goes in a group. Its libraries are not loaded before the main class; the extension loads the group once it knows it needs it:

```kotlin
// build.gradle.kts of the extension
attache {
    group("sqlite") { library("org.xerial:sqlite-jdbc:3.50.3.0") }
    group("postgres") { library("org.postgresql:postgresql:42.7.8") }
}
```

`AttacheLibraries.groups(id)` returns the `LibraryGroups` of an extension, which the host hands to it through [its own context](../index.md#a-context-of-your-own):

```java
ChassisHost<ModuleContext, Void> modules = new ChassisHost<>(options, ExampleModule.class,
        base -> new HostModuleContext(base, libraries.groups(base.descriptor().getId())), factory);
```

```java
// In the extension, with settings of its own:
context.groups().load(settings.database().toLowerCase(Locale.ROOT));
```

`libraries` is the host's `AttacheLibraries`, and `groups()` stands for the method the host's context gives it. `load` skips a group already loaded. It throws `IllegalArgumentException` when the extension declares no group of a name, and `IllegalStateException` when a library cannot be loaded or the extension does not run. The `LibraryGroups` holds only the extension's id, so a host may keep it; it reaches the extension's current load.

## What happens to the libraries

- A library the extension's loader already sees is skipped, so an extension does not load a second copy of what it already has.
- A library only the extension declares lands in its own loader. The host and other extensions do not see it. The extensions that depend on this one see it only when the extension [exports](../class-loading/index.md#one-extensions-class-loader) its packages, such as a database extension that exports `org.jdbi` with its API, so its dependents share its copy instead of bringing their own.
- When the extension stops, Chassis closes Attache's manager for it and then its loader; the libraries go with the loader. A reload loads them again from `folder`, without downloading them again.
- When a library cannot be downloaded or loaded, the extension fails to start, with Attache's reason, which names the library, as its failure.
