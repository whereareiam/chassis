---
title: Services
description: Offer objects between the host and its extensions by key, with priorities and watches, and know they go when their owner stops.
---

The host and its extensions offer each other objects through `Services`, by key. Every service has an owner: the host, or the extension that registered it. When an extension stops, its services go with it.

## Where to get the services

| Who | Call | Owner of what it registers |
| --- | --- | --- |
| the host | `ChassisHost.services()` | the host, under its kind word, such as `module`; stays until the host removes it |
| an extension | `context.services()` | the extension, under its id; removed when it stops |

Both see the same services; only the owner differs.

## Keys

A `ServiceKey` names a service by its type, and by a name when there are several services of one type:

```java
ServiceKey<Greeting> greeting = ServiceKey.of(Greeting.class);                 // named "default"
ServiceKey<Greeting> console = ServiceKey.of(Greeting.class, "console");
```

The type must be one both sides see: a type of a package the host exports, or of a package exported by an extension that the other side depends on. See [Types both sides see](../class-loading/index.md#types-both-sides-see).

## Registering and finding

```java
// In an extension's enable(ExtensionContext context):
ServiceRegistration registration = context.services().register(ServiceKey.of(Greeting.class), () -> "Hello");

// Anywhere that sees the services:
Optional<Greeting> found = services.find(ServiceKey.of(Greeting.class));
List<Greeting> every = services.all(ServiceKey.of(Greeting.class));
```

Several services may be registered under one key. `register(key, service, priority)` takes a priority; `register(key, service)` uses `0`. `find` returns the service with the highest priority, and among equal priorities the one registered first. `all` lists them in that order.

`register` throws `IllegalArgumentException` when the service is not an instance of the key's type.

Closing the `ServiceRegistration` removes the service; closing it again does nothing. An extension does not need to close its registrations: Chassis removes them when it stops, and on a failed `enable` as well.

## Following services

`watch` follows the services of one key. The listener is first told of every service registered so far, then of each that comes or goes:

```java
AutoCloseable watch = services.watch(ServiceKey.of(Greeting.class), event -> {
    if (event.isRegistered()) greetings.add(event.getService());
    else greetings.remove(event.getService());
});
```

A `ServiceEvent` carries `getKey()`, `getService()`, `getOwner()` (an extension's id, or the host's kind word), `getPriority()` and `isRegistered()`.

- A listener runs on the thread that made the change, after the change and outside the registry's lock, so it may register or find services itself.
- A listener that throws is logged as `A service listener failed`, and the other listeners are still told.
- A watch an extension holds ends when the extension stops. The stopping extension is not told about the services it takes away; every other watcher is.

A host that keeps services of extensions should keep them this way: an object kept after its owner stopped keeps the owner's class loader in memory.

## Example: a service from one extension to another

An extension can offer a service whose type lives in its own jar to the extensions that depend on it. It exports the type's package, the dependent sees the type through its [class loader](../class-loading/index.md#one-extensions-class-loader), and finds the service once its dependency has started:

```yaml
id: greeting
version: 1.4.0
main: org.example.greeting.GreetingModule
exports: [org.example.greeting.api]
```

```yaml
id: dependent
version: 1.0.0
main: org.example.dependent.DependentModule
dependencies:
  - id: greeting
    version: ^1
```

```java
@Override
public void enable(@NotNull ExtensionContext context) {
    Greeting greeting = context.services().find(ServiceKey.of(Greeting.class)).orElseThrow();
    context.logger().log(System.Logger.Level.INFO, greeting.text());
}
```

Because `greeting` starts first, its service is registered by the time `dependent` is enabled. When `greeting` stops, `dependent` stops before it.
