# Chassis

**Chassis** loads extensions into JVM hosts such as Minecraft plugins and mods. A host names its kind of extension, the folder their jars lie in and the descriptor each jar carries; Chassis finds the jars, resolves their dependencies, loads each in a class loader of its own that sees only what the host exports, starts and stops them in order, and removes everything an extension registered when it stops.

## A first host

The host declares its kind of extension and starts a `ChassisHost`:

```java
public interface ExampleModule extends Extension<ExtensionContext> {
}
```

```java
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

ChassisHost<ExtensionContext, Void> modules = new ChassisHost<>(options, ExampleModule.class, base -> base, ExtensionFactory.CONSTRUCTOR);
modules.start();
```

An extension is a jar in `modules/` with a descriptor and a main class of that kind:

```yaml
id: greeting
version: 1.0.0
main: com.example.greeting.GreetingModule
```

```java
public final class GreetingModule implements ExampleModule {
    @Override
    public void enable(@NotNull ExtensionContext context) {
        context.logger().log(System.Logger.Level.INFO, "Hello from " + context.descriptor().getName());
    }
}
```

## Dependency

Chassis is published to `https://registry.whereareiam.me/maven/packages` and needs Java 21:

```kotlin
repositories {
    maven("https://registry.whereareiam.me/maven/packages")
}

dependencies {
    implementation("me.whereareiam.chassis:chassis-common:0.1.0")
}
```

## Documentation

The guides for hosts and contributors are in `docs/content/`; start with [Getting started](docs/content/developers/index.md).

## License

Chassis is available under the [MIT License](LICENSE).
