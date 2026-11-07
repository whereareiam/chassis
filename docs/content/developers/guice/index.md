---
title: Guice
description: Make extensions through Guice, each with an injector of its own that offers what the host exports.
---

`chassis-adapter-guice` makes each extension's main class through a Guice injector of its own. The extension receives its context, its services and what the host exports from its own injector through constructor injection.

## Dependencies

The adapter does not bring Guice; the host ships its own Guice 7:

```kotlin
dependencies {
    implementation("me.whereareiam.chassis:chassis-common:0.1.0")
    implementation("me.whereareiam.chassis:chassis-adapter-guice:0.1.0")
    implementation("com.google.inject:guice:7.0.0")
}
```

Extensions write `jakarta.inject` annotations and never see Guice. Add `jakarta.inject` to the [export layer](../class-loading/index.md#the-export-layer) and keep it unrelocated; Guice itself may be relocated inside the host.

## Setting up the factory

Pass a `GuiceExtensionFactory` to `ChassisHost` in place of `ExtensionFactory.CONSTRUCTOR`:

```java
import com.google.inject.Injector;
import com.google.inject.Key;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.adapter.guice.GuiceExtensionFactory;
import me.whereareiam.chassis.common.ChassisHost;

ExtensionFactory factory = GuiceExtensionFactory.builder(hostInjector, ModuleContext.class)
        .export(Scheduler.class)
        .export(Key.get(Gateway.class))
        .bindings((binder, context) -> binder.bind(ModuleSettings.class).toInstance(settingsOf(context)))
        .build();

ChassisHost<ModuleContext> modules = new ChassisHost<>(options, ExampleModule.class,
        base -> new HostModuleContext(base, scheduler), factory);
```

`Scheduler`, `Gateway`, `ModuleSettings` and `settingsOf` stand for the host's own types and code; `ModuleContext` and `HostModuleContext` are the host's context, as in [Getting started](../index.md#a-context-of-your-own). A host that gives the base context passes `ExtensionContext.class` and `base -> base`.

## What an extension's injector offers

| Binding | Value |
| --- | --- |
| the context type given to `builder`, and `ExtensionContext` | the context of this load |
| `Services` | the services as this extension, so what it registers is removed when it stops |
| `ExtensionDescriptor` | the extension's descriptor |
| each key passed to `export` | asked from the host's injector every time it is injected, so the host's scopes apply |
| what `bindings` adds | made from the context of this load |

An exported key's type must be one the extension sees through the export layer. The injector is made in Guice's production stage, so the extension's singletons are made eagerly when it starts.

## The extension

```java
package org.example.guice;

import com.example.host.api.ExampleModule;
import com.example.host.api.ModuleContext;
import com.example.host.api.Scheduler;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;

public final class InjectedModule implements ExampleModule {
    private final Scheduler scheduler;
    private final Helper helper;

    @Inject
    public InjectedModule(Scheduler scheduler, Helper helper) {
        this.scheduler = scheduler;
        this.helper = helper;
    }

    @Override
    public void enable(@NotNull ModuleContext context) {
        context.logger().log(System.Logger.Level.INFO, helper.greet());
    }

    public static final class Helper {
        public String greet() {
            return "hello";
        }
    }
}
```

`Helper` has no binding: Guice makes it just in time, in the extension's own injector.

## Why no child injector

The extension's injector is never a child of the host's. Guice puts a child injector's just-in-time bindings into its parent whenever the parent can satisfy them, so the host's injector would keep classes of the extension, such as `Helper` above, and with them the extension's class loader, after the extension stopped. A host that makes injectors for extensions by other means runs into the same leak with `createChildInjector`.

The adapter's own test starts an extension like the one above, disables it, and checks with [`LeakCheck`](../testing/index.md#checking-that-a-stopped-extension-is-collected) that its class loader is collected and that the host's injector holds no binding of the extension's classes.
