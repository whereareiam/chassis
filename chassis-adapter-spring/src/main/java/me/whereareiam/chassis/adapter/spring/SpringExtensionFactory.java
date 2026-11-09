package me.whereareiam.chassis.adapter.spring;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes each extension through a Spring context of its own, a child of the host's context. The extension's main class
 * is that context's configuration: it must be a Spring {@code @Configuration}, directly or through an annotation of
 * the host that carries one, and its own annotations ({@code @ComponentScan}, {@code @Import}) decide what else the
 * context holds.
 *
 * <pre>{@code
 * ChassisHost<ModuleContext> modules = new ChassisHost<>(options, ExampleModule.class,
 *         base -> new HostModuleContext(base, scheduler), new SpringExtensionFactory(hostContext));
 * }</pre>
 *
 * <p>An extension's context holds:</p>
 * <ul>
 *   <li>its {@link ExtensionContext}, as the type the host made it (the host's own context type included), its
 *   {@link ExtensionDescriptor} and its {@link me.whereareiam.chassis.service.Services};</li>
 *   <li>the beans of each enabled dependency whose type, a superclass or an interface lies in a package that dependency
 *   exports;</li>
 *   <li>through its parent, every bean of the host's context, of which the extension can use those whose types the
 *   host exports.</li>
 * </ul>
 *
 * <p>The context closes when the extension stops or fails to start.</p>
 */
public final class SpringExtensionFactory implements ExtensionFactory {
	private static final String REGISTERED = "chassis.";

	private final @NotNull ApplicationContext host;
	// The context of every extension it made that has not stopped, by id.
	private final Map<String, Made> made = new ConcurrentHashMap<>();

	/**
	 * Makes the factory.
	 *
	 * @param host the host's context, the parent of every extension's context
	 */
	public SpringExtensionFactory(@NotNull ApplicationContext host) {
		this.host = host;
	}

	@Override
	public <T> @NotNull T create(@NotNull Class<T> main, @NotNull ExtensionContext context) {
		ExtensionDescriptor descriptor = context.descriptor();
		if (!AnnotatedElementUtils.hasAnnotation(main, Configuration.class))
			throw new IllegalStateException("The main class " + main.getName() + " of '" + descriptor.getId() + "' is no Spring @Configuration");

		AnnotationConfigApplicationContext extension = new AnnotationConfigApplicationContext();
		extension.setId("chassis-extension:" + descriptor.getId());
		extension.setParent(host);
		extension.setClassLoader(main.getClassLoader());
		Made own = new Made(descriptor, extension);
		made.put(descriptor.getId(), own);
		context.onClose(() -> close(own));

		ConfigurableListableBeanFactory beans = extension.getBeanFactory();
		beans.registerSingleton(REGISTERED + "context", context);
		beans.registerSingleton(REGISTERED + "descriptor", descriptor);
		beans.registerSingleton(REGISTERED + "services", context.services());
		for (ExtensionDescriptor.Dependency dependency : descriptor.getDependencies()) {
			Made source = made.get(dependency.getId());
			if (source != null) exportTo(beans, source);
		}

		extension.register(main);
		extension.refresh();

		return extension.getBean(main);
	}

	// Hands the dependency's own beans of exported types to the dependent's context, under the dependency's id.
	private static void exportTo(ConfigurableListableBeanFactory target, Made source) {
		ConfigurableListableBeanFactory beans = source.context.getBeanFactory();
		for (String name : beans.getBeanNamesForType(Object.class, false, false)) {
			if (name.startsWith(REGISTERED)) continue;

			Class<?> type = beans.getType(name);
			if (type == null || !exported(type, source.descriptor)) continue;

			target.registerSingleton(REGISTERED + source.descriptor.getId() + "." + name, beans.getBean(name));
		}
	}

	private static boolean exported(Class<?> type, ExtensionDescriptor descriptor) {
		for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass())
			if (inExports(current, descriptor)) return true;
		for (Class<?> contract : ClassUtils.getAllInterfacesForClassAsSet(type))
			if (inExports(contract, descriptor)) return true;

		return false;
	}

	private static boolean inExports(Class<?> type, ExtensionDescriptor descriptor) {
		String name = type.getName();
		for (String exported : descriptor.getExports())
			if (name.startsWith(exported + ".")) return true;

		return false;
	}

	private void close(Made own) {
		made.remove(own.descriptor.getId(), own);
		own.context.close();
		CachedIntrospectionResults.clearClassLoader(own.context.getClassLoader());
	}

	private record Made(@NotNull ExtensionDescriptor descriptor, @NotNull AnnotationConfigApplicationContext context) {
	}
}
