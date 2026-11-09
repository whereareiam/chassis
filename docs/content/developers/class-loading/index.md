---
title: What extensions see
description: The export layer, the class loader of each extension, and what keeps a stopped extension's classes alive.
---

Each extension runs in a class loader of its own. That loader sees the JDK, the packages the host exports, the extension's own jar, the jars a [preparation](#preparing-a-loader) added and the jars of the extensions it depends on, and nothing else of the host: not its private libraries, not the platform, not other extensions.

## The export layer

`ExportLayer` is the parent of every extension's class loader. It answers from the JDK, and hands every class or resource of an exported package to the loader the host names, usually its own:

```java
new ExportLayer(ExampleHost.class.getClassLoader(), List.of(
        "me.whereareiam.chassis",
        "com.example.host.api",
        "net.kyori.adventure"
));
```

A package is exported with its subpackages: `net.kyori.adventure` exports `net.kyori.adventure.text` too.

What to export:

- `me.whereareiam.chassis`, so extensions see `Extension`, `ExtensionContext`, `Services` and the other types of Chassis's API. Its subpackages come along, including `me.whereareiam.chassis.common`.
- The packages of the host's API: its kind of extension, its context and the types of the services it offers.
- Libraries the host shares with extensions, under their original package names. A relocated copy is a different package, and extensions compiled against the original names do not find it.
- `jakarta.inject` when the host makes extensions through [Guice](../guice/index.md).

Leave everything else out. A library the host uses internally stays invisible to extensions, so each extension can bring its own version of it.

## One extension's class loader

An extension's loader looks for a class in this order:

1. the export layer: the JDK and the exported packages;
2. the extension's own jar, then the jars a preparation added;
3. the jars of the extensions it depends on, and the jars those depend on in turn.

The third step covers every dependency that is enabled when the extension starts, optional dependencies included, so an extension uses the classes of a dependency directly:

```java
// In the extension "dependent", which declares a dependency on "greeting":
import org.example.greeting.Shared;

String greeting = Shared.GREETING;
```

Because the export layer comes first, an extension cannot replace a class the host exports. A jar that bundles its own copy of an exported library gets the host's copy at runtime, and a class of the extension that lies in an exported package is taken from the host. Keep extensions in packages of their own.

A class that is neither exported nor in the extension's jar or its dependencies' jars is missing: loading the main class or running `enable` throws `NoClassDefFoundError`, and the extension is `FAILED` with that error as its failure. Either export the package from the host or shade the library into the extension's jar.

## Preparing a loader

A host can prepare each extension's loader before Chassis loads the extension's main class, with an `ExtensionPreparation` in `HostOptions.preparation`. It gets the new loader with the extension's descriptor and jar, may add jars to it, and returns what to close when that load ends:

```java
HostOptions options = HostOptions.builder()
        // ...
        .preparation(loader -> {
            for (Path library : librariesOf(loader.descriptor()))
                loader.addJar(library);
            return () -> {};
        })
        .build();
```

A jar added this way comes after the extension's own jar, and the export layer still comes first, so it cannot replace a class the host exports. Chassis closes what the preparation returned when the extension stops, after what the extension handed to `onClose` and before the loader, and also when the extension fails to start. A preparation that throws makes the extension `FAILED` with that error.

## Types both sides see

An object can pass between the host and an extension, or between two extensions, only as a type both sides load from the same place. That is a type of an exported package, or a type of an extension that the other side depends on. The same rule applies to [service keys](../services/index.md): a service registered as `ServiceKey.of(Greeting.class)` is found only by code that sees the same `Greeting` class.

## When an extension stops

When an extension stops, Chassis calls its `disable()`, closes what it handed to `onClose` and removes its services and watches, then closes its class loader. Its classes are collected once nothing else refers to them.

Chassis releases only what went through the context. These keep a stopped extension's loader, and every class in it, in memory:

- a thread the extension started and did not stop;
- a listener the extension registered with the platform or a library directly, outside its context;
- an object of the extension that the host keeps, such as a service it found and stored in a field;
- a cache of the host or of a shared library that holds the extension's classes.

Give the context everything that must end with the extension:

```java
@Override
public void enable(@NotNull ExtensionContext context) {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    context.onClose(executor::shutdownNow);
}
```

A host that holds services of extensions [watches them](../services/index.md#following-services) and drops each when it goes. [`LeakCheck`](../testing/index.md#checking-that-a-stopped-extension-is-collected) proves in a test that a stopped extension's loader is collected.
