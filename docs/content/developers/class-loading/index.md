---
title: What extensions see
description: The export layer, the class loader of each extension, and what keeps a stopped extension's classes alive.
---

Each extension runs in a class loader of its own. That loader sees the JDK, the packages the host exports, the extension's own jar, the jars a [preparation](#preparing-a-loader) added and the packages the extensions it depends on export, and nothing else of the host: not its private libraries, not the platform, not other extensions.

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
<<<<<<< HEAD
2. the [packages it names of the platform's peers](#packages-of-peers), from each peer's own class loader;
3. the extension's own jar, then the jars a preparation added;
4. the packages the extensions it depends on export, and those that their own dependencies export in turn.
=======
2. the [packages it names of the platform's peers](#packages-of-peers), from each peer's own class loader;
3. the packages the extensions it depends on export, and those that their own dependencies export in turn;
4. the extension's own jar, then the jars a preparation added.
>>>>>>> 33004c3 (feat(adapter): extensions load their Attache groups when they need them)

The third step covers every dependency that is enabled when the extension starts, optional dependencies included. A dependency names the packages its dependents see under `exports` in its [descriptor](../descriptor/index.md), each with its subpackages:

```yaml
id: greeting
version: 1.4.0
main: org.example.greeting.GreetingModule
exports: [org.example.greeting.api]
```

An extension then uses those classes of the dependency directly:

```java
// In the extension "dependent", which declares a dependency on "greeting":
import org.example.greeting.api.Shared;

String greeting = Shared.GREETING;
```

Nothing else of the dependency is visible. An exported package may lie in a jar a preparation added for it, such as a library it shares with its dependents; its other libraries stay its own. An extension that exports nothing gives its dependents no classes; they can still use the services it registers under types the host exports. Keep what dependents compile against in packages of its own, such as an `api` package, and export those.

Because the export layer comes first, an extension cannot replace a class the host exports. A jar that bundles its own copy of an exported library gets the host's copy at runtime, and a class of the extension that lies in an exported package is taken from the host. The same holds for the packages a dependency exports: they come before the extension's own jar, so objects of them pass between the two. Keep extensions in packages of their own.

A class that is neither exported by the host, nor in the extension's own jars, nor exported by a dependency is missing: loading the main class or running `enable` throws `NoClassDefFoundError`, and the extension is `FAILED` with that error as its failure. Either export the package from the host or shade the library into the extension's jar.

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

A jar added this way comes after the extension's own jar, and the export layer still comes first, so it cannot replace a class the host exports. It stays the extension's own: the extensions that depend on it never see its classes. Chassis closes what the preparation returned when the extension stops, after what the extension handed to `onClose` and before the loader, and also when the extension fails to start. A preparation that throws makes the extension `FAILED` with that error.

## Transforming a jar

A host can change each extension's jar before Chassis makes its loader, with `ExtensionTransformer`s in `HostOptions.transformer`, such as to relocate a package the host ships under another name, so that extensions compiled against the original name find the host's copy. Chassis does not know what a transformer does: it hands each one the extension's descriptor, the jar and an empty file to write the result to.

```java
ExtensionTransformer relocation = new ExtensionTransformer() {
    @Override
    public @NotNull String getId() {
        return "adventure-relocation";
    }

    @Override
    public @NotNull String getVersion() {
        return "1";
    }

    @Override
    public @NotNull Path transform(@NotNull ExtensionDescriptor extension, @NotNull Path input, @NotNull Path output) throws IOException {
        relocate(input, output, "net.kyori.adventure", "org.example.library.adventure");
        return output;
    }
};

HostOptions options = HostOptions.builder()
        // ...
        .transformer(relocation)
        .build();
```

- Transformers run in the order the host names them, each on the result of the previous one, every time an extension starts. `appliesTo(descriptor)` leaves an extension out without reading its jar.
- A transformer returns `output` when it wrote it, or `input` when the jar needs no change.
- The result is kept in `HostOptions.transformedFolder`, `<dataFolder>/.transformed` by default, as `<jar name>-<id>-<version>.jar`, with a `.source.sha256` marker that holds the SHA-256 of the jar it was made from. A start reuses the result while that jar is unchanged, so a restart or a reload does not transform it again; change `getVersion()` whenever the transformer writes different bytes for the same input. A jar the transformer left unchanged is remembered too.
- The loader and the [preparation](#preparing-a-loader) read the transformed jar; the installed jar and `InstalledExtension.getJar()` stay as they were.
- A transformer that throws makes the extension `FAILED`, with a failure that names the transformer and the jar.

## Packages of peers

A peer is what the platform loads beside the host, such as a Bukkit or Velocity plugin or a NeoForge mod. On platforms where each peer has a class loader of its own, such as Bukkit and Velocity, an extension uses a peer's API by naming its packages under `packages` in the peer's entry, each with its subpackages:

```yaml
peers:
  - id: LuckPerms
    version: ^5
    packages: [net.luckperms.api]
```

```java
// In the extension, with LuckPerms's API on its compile class path:
LuckPerms luckPerms = LuckPermsProvider.get();
```

- Those packages come from the peer's own class loader whenever the peer is loaded, optional peers included, and before the extension's own jar: the extension uses the very classes the peer uses, so objects pass between them, even when it bundles a copy of the API.
- Nothing else of the peer is visible: not its other packages and not the libraries it shades, so an extension never binds to a peer's internals by accident.
- An entry without `packages` only makes the extension wait for the peer.

The host needs no dependency on the peer. On NeoForge every mod shares the host's loader, so there a host [exports a mod's API](../platforms/index.md#exporting-another-mods-api) instead.

## Types both sides see

An object can pass between the host and an extension, or between two extensions, only as a type both sides load from the same place. That is a type of a package the host exports, or of a package exported by an extension that the other side depends on. The same rule applies to [service keys](../services/index.md): a service registered as `ServiceKey.of(Greeting.class)` is found only by code that sees the same `Greeting` class.

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
