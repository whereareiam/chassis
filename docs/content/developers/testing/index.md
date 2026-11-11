---
title: Testing a host
description: Start a real ChassisHost in a unit test with extension jars written on the fly, and prove that stopped extensions are collected.
---

`chassis-testing` lets a host's tests load real extension jars without building them separately. `ExtensionJar` writes a jar from a descriptor and classes of the test's own class path; `LeakCheck` tells whether a stopped extension's class loader was collected.

```kotlin
dependencies {
    testImplementation("me.whereareiam.chassis:chassis-testing:0.1.0")
}
```

## Writing an extension jar

Write the extension's classes as ordinary test sources, in a package the host does not export, such as `org.example.greeting`:

```java
package org.example.greeting;

import com.example.host.api.ExampleModule;
import me.whereareiam.chassis.ExtensionContext;
import org.jetbrains.annotations.NotNull;

public final class GreetingModule implements ExampleModule {
    @Override
    public void enable(@NotNull ExtensionContext context) {
        context.onClose(() -> System.setProperty("example.greeting.closed", "true"));
    }
}
```

Then write a jar of them in the test:

```java
Path jar = ExtensionJar.of("example-module.yml", """
        id: greeting
        version: 1.0.0
        main: org.example.greeting.GreetingModule
        """)
        .with(GreetingModule.class)
        .write(folder.resolve("modules/greeting.jar"));
```

- `of(descriptorFile, text)` starts the jar with its descriptor at the path the host names.
- `with(classes...)` adds classes with their nested, local and anonymous classes. List every top-level class the extension needs.
- `file(path, text)` adds any other text file.
- `write(target)` writes the jar, creating the folders and replacing a jar already there, which is how a test prepares a `reload`.

The classes must not lie in a package the host exports: the export layer comes first, so the host's copy from the test class path would be used instead of the jar's.

## Starting the host in a test

Point `HostOptions` at a temporary folder, export the packages of Chassis and of the host's API from the test's class loader, and give a platform the test controls:

```java
import com.example.host.api.ExampleModule;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.common.ChassisHost;
import me.whereareiam.chassis.common.HostOptions;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
import me.whereareiam.chassis.testing.ExtensionJar;
import me.whereareiam.chassis.testing.LeakCheck;
import me.whereareiam.chassis.type.ExtensionState;
import org.example.greeting.GreetingModule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleHostTest {
    @TempDir
    Path folder;

    @Test
    void aDisabledExtensionIsClosedAndCollected() throws Exception {
        ExtensionJar.of("example-module.yml", """
                id: greeting
                version: 1.0.0
                main: org.example.greeting.GreetingModule
                """).with(GreetingModule.class).write(folder.resolve("modules/greeting.jar"));
        ChassisHost<ExtensionContext, Void> host = new ChassisHost<>(options(), ExampleModule.class, base -> base,
                ExtensionFactory.CONSTRUCTOR);

        host.start();
        assertEquals(ExtensionState.ENABLED, host.find("greeting").orElseThrow().getState());

        WeakReference<ClassLoader> loader = new WeakReference<>(host.classLoader("greeting"));
        host.disable("greeting");

        assertEquals("true", System.getProperty("example.greeting.closed"));
        assertTrue(LeakCheck.collected(loader, Duration.ofSeconds(10)), "the host kept the extension");
    }

    private HostOptions options() {
        return HostOptions.builder()
                .kind("module")
                .folder(folder.resolve("modules"))
                .descriptorFile("example-module.yml")
                .dataFolder(folder.resolve("data"))
                .selectionFile(folder.resolve("modules.yml"))
                .hostVersion(Version.parse("1.0.0"))
                .platform(new Platform() {
                    @Override
                    public @NotNull String name() {
                        return "test";
                    }

                    @Override
                    public @NotNull Optional<PlatformPeer> peer(@NotNull String id) {
                        return Optional.empty();
                    }
                })
                .exportLayer(new ExportLayer(getClass().getClassLoader(),
                        List.of("me.whereareiam.chassis", "com.example.host.api")))
                .build();
    }
}
```

What else a test can check:

- a state, and why: `getState()`, `getWaitingFor()` and `getFailure().getMessage()` of `host.find(id)`;
- the order of changes, by collecting `host.subscribe(...)` events;
- that services went: `host.services().find(key)` is empty after the owner stops;
- that a turned-off extension stays off: start a second host on the same folder, or read `new SelectionFile(path, kind).disabled()`.

Extensions report back to the test through what both sides see: services of the host's API, or system properties as above. A static field of an extension's class is not the field the test sees, because the extension's class comes from the jar.

## Checking that a stopped extension is collected

`LeakCheck.collected(reference, timeout)` asks the JVM to collect garbage until the referent is gone or the time is up, and returns whether it is gone. Take a weak reference to the extension's class loader while it runs, stop it with `disable`, `reload` or `host.stop()`, then check:

```java
WeakReference<ClassLoader> loader = new WeakReference<>(host.classLoader("greeting"));
host.reload("greeting");
assertTrue(LeakCheck.collected(loader, Duration.ofSeconds(10)));
```

A collected loader proves that nothing the host keeps still refers to the extension's classes. When the check fails, look for what [keeps a stopped extension in memory](../class-loading/index.md#when-an-extension-stops). The test itself must not hold the extension either: a local variable with the result of `host.instance(id)`, or a service of the extension, keeps the loader alive.
