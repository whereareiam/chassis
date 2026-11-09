---
title: Spring
description: Make extensions through Spring, each with a child context of the host's that holds its own beans and what its dependencies export.
---

`chassis-adapter-spring` makes each extension through a Spring context of its own, a child of the host's context. The extension's main class is that context's configuration: Spring makes it, injects it, and scans or imports whatever its annotations say.

## Dependencies

The adapter does not bring Spring; the host ships its own Spring Framework 7:

```kotlin
dependencies {
    implementation("me.whereareiam.chassis:chassis-common:0.1.0")
    implementation("me.whereareiam.chassis:chassis-adapter-spring:0.1.0")
    implementation("org.springframework:spring-context:7.0.10")
}
```

Add `org.springframework` to the [export layer](../class-loading/index.md#the-export-layer) and keep it unrelocated: extensions use Spring's annotations, and the classes Spring generates for a configuration live in the extension's loader and refer to Spring's.

## Setting up the factory

Pass a `SpringExtensionFactory` with the host's context to `ChassisHost` in place of `ExtensionFactory.CONSTRUCTOR`:

```java
import me.whereareiam.chassis.adapter.spring.SpringExtensionFactory;
import me.whereareiam.chassis.common.ChassisHost;

ChassisHost<ModuleContext> modules = new ChassisHost<>(options, ExampleModule.class,
        base -> new HostModuleContext(base, scheduler), new SpringExtensionFactory(hostContext));
```

## The extension

The main class must be a Spring `@Configuration`. A host usually gives its extensions an annotation of its own that carries it, so an extension writes one word:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Configuration
@ComponentScan
public @interface Module {
}
```

```java
@Module
public class GreetingModule implements ExampleModule {
    private final Words words;

    public GreetingModule(Words words) {
        this.words = words;
    }

    @Override
    public void enable(@NotNull ModuleContext context) {
        context.logger().log(System.Logger.Level.INFO, words.greeting());
    }
}
```

`@ComponentScan` without packages scans the main class's package and its subpackages, in the extension's own jar only. A main class that is no `@Configuration`, directly or through such an annotation, makes the extension fail to start with `The main class <class> of '<id>' is no Spring @Configuration`.

## What an extension's context offers

| Bean | Value |
| --- | --- |
| its context, as `ExtensionContext` and as the host's own context type | the context of this load |
| `Services` | the services as this extension, so what it registers is removed when it stops |
| `ExtensionDescriptor` | the extension's descriptor |
| the beans of each enabled dependency whose type, a superclass or an interface lies in a package that dependency [exports](../descriptor/index.md#fields) | the dependency's own instances |
| every bean of the host's context, through the parent | usable when the extension sees its type through the export layer |

A dependency's other beans stay out of the dependent's context, so a dependent injects a dependency's exported interface and never its implementation:

```yaml
id: greeting
version: 1.0.0
main: org.example.greeting.GreetingModule
exports: [org.example.greeting.api]
```

```java
@Module
public class UserModule implements ExampleModule {
    public UserModule(Words words) {   // org.example.greeting.api.Words, made in greeting's context
        ...
    }
}
```

## When an extension stops

Its context closes when the extension stops or fails to start: Spring destroys its beans, and the adapter clears what Spring cached about the extension's classes. A dependency stops only after its dependents, so a dependent's copies of its beans are gone first. The adapter's own test starts a dependency and a dependent, stops them, and checks with [`LeakCheck`](../testing/index.md#checking-that-a-stopped-extension-is-collected) that both class loaders are collected.
