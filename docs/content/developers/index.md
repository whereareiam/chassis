---
title: Getting started
description: Load your own kind of extension into a plugin or mod with Chassis.
---

Chassis loads extensions into a host: a plugin, mod or application that names its own kind of extension, such as providers or modules, the folder their jars lie in and the descriptor file each jar carries. Chassis finds the jars, reads their descriptors, decides which can start, loads each in a class loader of its own, starts and stops them in the order of their dependencies, and removes everything an extension registered once it stops.

This page sets up a host and loads a first extension into it. To change Chassis itself, see [Contributing](../contributing/building/index.md).

## Dependencies

Chassis is published to the [public Maven registry](https://registry.whereareiam.me/maven/packages) under the group `me.whereareiam.chassis`, and needs Java 21.

| Artifact | Who depends on it | What it holds |
| --- | --- | --- |
| `chassis-api` | extensions, through the host's API | `Extension`, `ExtensionContext`, `Services`, `Extensions`, descriptors and states |
| `chassis-common` | the host, shipped inside it | `ChassisHost`, `HostOptions`, `ExportLayer`, `SelectionFile` |
| `chassis-adapter-guice` | a host that makes extensions through Guice | `GuiceExtensionFactory`; see [Guice](./guice/index.md) |
| `platform-neoforge` | a host on NeoForge | `NeoForgePlatform`; see [NeoForge](./neoforge/index.md) |
| `chassis-testing` | the host's tests | `ExtensionJar`, `LeakCheck`; see [Testing a host](./testing/index.md) |

A host ships `chassis-common` (which brings `chassis-api`) inside its own jar:

```kotlin
repositories {
    maven("https://registry.whereareiam.me/maven/packages")
}

dependencies {
    implementation("me.whereareiam.chassis:chassis-common:0.1.0")
    implementation("me.whereareiam.chassis:platform-neoforge:0.1.0")

    testImplementation("me.whereareiam.chassis:chassis-testing:0.1.0")
}
```

An extension compiles against the host's API and `chassis-api` and ships neither; the host provides both at runtime:

```kotlin
dependencies {
    compileOnly("me.whereareiam.chassis:chassis-api:0.1.0")
}
```

Extensions refer to Chassis's API by its package names, so a host that relocates its libraries leaves `me.whereareiam.chassis` where it is.

## Name your kind of extension

A host loads only main classes of its own kind. Declare that kind in the host's API, the package extensions compile against:

```java
package com.example.host.api;

import me.whereareiam.chassis.Extension;
import me.whereareiam.chassis.ExtensionContext;

public interface ExampleModule extends Extension<ExtensionContext> {
}
```

`ExtensionContext` is what Chassis gives every extension: its descriptor, its data folder, a logger, the services and `onClose`. To give extensions more, see [A context of your own](#a-context-of-your-own).

## Start the host

Describe how the host loads its extensions with `HostOptions`, then start a `ChassisHost` when the host starts and stop it when the host stops:

```java
package com.example.host;

import com.example.host.api.ExampleModule;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.common.ChassisHost;
import me.whereareiam.chassis.common.HostOptions;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.neoforge.NeoForgePlatform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class ExampleHost {
    private ChassisHost<ExtensionContext> modules;

    public void start(Path dataFolder) throws IOException {
        HostOptions options = HostOptions.builder()
                .kind("module")
                .folder(dataFolder.resolve("modules"))
                .descriptorFile("example-module.yml")
                .dataFolder(dataFolder.resolve("module-data"))
                .selectionFile(dataFolder.resolve("modules.yml"))
                .hostVersion(Version.parse("1.0.0"))
                .platform(new NeoForgePlatform())
                .exportLayer(new ExportLayer(ExampleHost.class.getClassLoader(),
                        List.of("me.whereareiam.chassis", "com.example.host.api")))
                .build();

        modules = new ChassisHost<>(options, ExampleModule.class, base -> base, ExtensionFactory.CONSTRUCTOR);
        modules.start();
    }

    public void stop() {
        modules.stop();
    }
}
```

| Option | Meaning |
| --- | --- |
| `kind` | The host's word for its extensions. It appears in log messages and in the selection file, and it names the host as the owner of the services it registers. |
| `folder` | Where the extension jars lie. Chassis creates it when it is missing and reads every `.jar` directly in it, in the order of their file names. |
| `descriptorFile` | The descriptor's path inside a jar. See [Descriptor](./descriptor/index.md). |
| `dataFolder` | The folder that holds each extension's own folder, `<dataFolder>/<id>`. |
| `selectionFile` | The file that keeps which extensions an administrator turned off. See [Selection file](./selection/index.md). |
| `hostVersion` | The version of the host's API, which a descriptor's `host` range must accept. |
| `offer(name, version)` | Something the host offers for descriptors' `requires`; call it once per offer. |
| `platform` | The platform the host runs on. See [NeoForge](./neoforge/index.md) and [Other platforms](./neoforge/index.md#other-platforms). |
| `exportLayer` | The packages extensions see of the host. See [What extensions see](./class-loading/index.md). |
| `preparation` | Prepares each extension's class loader before its main class is loaded, such as adding the libraries it declares. Defaults to `ExtensionPreparation.NONE`. See [What extensions see](./class-loading/index.md#preparing-a-loader). |
| `loggers` | Gives the `System.Logger` of a name: the host's kind word or an extension's id. Defaults to `System::getLogger`. |

The last two constructor arguments make each load: `base -> base` hands every extension the context Chassis keeps, and `ExtensionFactory.CONSTRUCTOR` makes the main class through its public constructor without parameters. [Guice](./guice/index.md) replaces the factory.

`ChassisHost` makes every change under one lock on the caller's thread. Call `start`, `stop` and the [control API](./control/index.md) from the thread the host starts and stops its extensions on.

## Write an extension

An extension is a jar with the descriptor at its root and a main class of the host's kind:

```yaml
id: greeting
name: Greeting
version: 1.0.0
main: com.example.greeting.GreetingModule
host: ^1
```

```java
package com.example.greeting;

import com.example.host.api.ExampleModule;
import me.whereareiam.chassis.ExtensionContext;
import org.jetbrains.annotations.NotNull;

public final class GreetingModule implements ExampleModule {
    @Override
    public void enable(@NotNull ExtensionContext context) {
        context.logger().log(System.Logger.Level.INFO, "Hello from " + context.descriptor().getName());
    }
}
```

`disable()` has an empty default. Override it only to release what the extension made outside its context; what it registered through the context, Chassis removes.

Keep the extension's classes out of the packages the host exports; a class in an exported package is always taken from the host.

Put the jar into the host's `modules` folder and start the host. The host's log shows `Enabled the module greeting 1.0.0`, and `modules.find("greeting")` returns the extension in the state `ENABLED`.

## When it does not start

Every installed extension has a state. Read it through `find` or `list`:

```java
modules.find("greeting").ifPresent(extension -> {
    System.out.println(extension.getState());      // ENABLED, DISABLED, WAITING or FAILED
    System.out.println(extension.getWaitingFor()); // why it waits, one line per missing piece
    System.out.println(extension.getFailure());    // why it failed, or null
});
```

| What you see | Next check |
| --- | --- |
| No entry for the id | The jar is not directly in `folder`, or does not end in `.jar`. A jar without a readable descriptor is listed under its file name without `.jar`. |
| `FAILED` with `... has no example-module.yml` | The descriptor is not at the path `descriptorFile` names. |
| `FAILED` with `... is no com.example.host.api.ExampleModule` | The main class does not implement the host's kind, or implements a copy of it from the extension's own jar because the host's API package is missing from the export layer. |
| `FAILED` with a `NoClassDefFoundError` | The extension uses a class the host does not export and its jar does not contain. See [What extensions see](./class-loading/index.md). |
| `WAITING` | `getWaitingFor()` names each dependency or plugin it waits for. See [Lifecycle and states](./lifecycle/index.md). |

A failure to start is also logged with its stack trace as `The module greeting failed to start`.

## A context of your own

A host gives its extensions more than the base context, such as its scheduler or configuration, by extending `ExtensionContext` and naming that context in its kind:

```java
// com/example/host/api/ModuleContext.java
public interface ModuleContext extends ExtensionContext {
    Scheduler scheduler();
}

// com/example/host/api/ExampleModule.java
public interface ExampleModule extends Extension<ModuleContext> {
}
```

The host's implementation hands the base methods to the context Chassis keeps, which tracks what the extension registers, and adds its own. It lives in the host, outside the exported packages:

```java
final class HostModuleContext implements ModuleContext {
    private final ExtensionContext base;
    private final Scheduler scheduler;

    HostModuleContext(ExtensionContext base, Scheduler scheduler) {
        this.base = base;
        this.scheduler = scheduler;
    }

    @Override public @NotNull ExtensionDescriptor descriptor() { return base.descriptor(); }
    @Override public @NotNull Path dataFolder() { return base.dataFolder(); }
    @Override public @NotNull System.Logger logger() { return base.logger(); }
    @Override public @NotNull Services services() { return base.services(); }
    @Override public void onClose(@NotNull AutoCloseable resource) { base.onClose(resource); }
    @Override public Scheduler scheduler() { return scheduler; }
}
```

```java
ChassisHost<ModuleContext> modules = new ChassisHost<>(options, ExampleModule.class,
        base -> new HostModuleContext(base, scheduler), ExtensionFactory.CONSTRUCTOR);
```

`Scheduler` stands for any type of the host's API. When something the host hands out has to be released as the extension stops, such as a scheduled task, register it with `base.onClose(...)` inside the context, so it is closed even when the extension forgets.

## Next steps

| You want to | Read |
| --- | --- |
| declare dependencies, versions and requirements of an extension | [Descriptor](./descriptor/index.md) |
| decide which classes of the host extensions see | [What extensions see](./class-loading/index.md) |
| know when an extension starts, waits, fails or stops | [Lifecycle and states](./lifecycle/index.md) |
| let administrators turn extensions on and off | [Control API](./control/index.md) and [Selection file](./selection/index.md) |
| share objects between the host and its extensions | [Services](./services/index.md) |
| test the host with real extension jars | [Testing a host](./testing/index.md) |
