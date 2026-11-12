package me.whereareiam.chassis.common;

import me.whereareiam.chassis.Extension;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.Extensions;
import me.whereareiam.chassis.common.context.ContextFactory;
import me.whereareiam.chassis.common.context.FieldsContextFactory;
import me.whereareiam.chassis.common.discovery.ExtensionDiscovery;
import me.whereareiam.chassis.common.lifecycle.ExtensionControl;
import me.whereareiam.chassis.common.lifecycle.ExtensionEntries;
import me.whereareiam.chassis.common.lifecycle.ExtensionEvents;
import me.whereareiam.chassis.common.lifecycle.ExtensionLauncher;
import me.whereareiam.chassis.common.lifecycle.ExtensionSettlement;
import me.whereareiam.chassis.common.loading.JarTransformation;
import me.whereareiam.chassis.common.resolution.DependencyResolver;
import me.whereareiam.chassis.common.service.ServiceRegistry;
import me.whereareiam.chassis.event.ExtensionEvent;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.service.Services;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A host's extensions: it finds their jars, decides what can start, loads each in a class loader of its own over the
 * host's export layer, starts and stops them in the order of their dependencies, and removes what each registered when
 * it stops. It is the {@link Extensions} a host hands to administrators.
 *
 * <pre>{@code
 * ChassisHost<ModuleContext, Void> modules = new ChassisHost<>(options, ArborModule.class, base -> new ArborModuleContext(base, ...), factory);
 * modules.start();
 * modules.stop();
 * }</pre>
 *
 * <p>Every change is made under one lock, on the caller's thread; a host calls it from the thread it starts and stops
 * extensions on.</p>
 *
 * <p>It logs what an administrator acts on: an extension that waits, cannot start or did not stop cleanly, with why.
 * Each start and stop is a debug line; a host says in its own words what it started, from {@link #list()}.</p>
 *
 * <p>A host that reads fields of its own from each descriptor, such as where updates of an extension come from, names
 * their type; it gets them typed, when it makes an extension's context and from {@link #fields(String)}.</p>
 *
 * <pre>{@code
 * ChassisHost<ModuleContext, ModuleFields> modules = new ChassisHost<>(options, ArborModule.class, ModuleFields.class,
 *         (base, fields) -> new ArborModuleContext(base, ...), factory);
 * }</pre>
 *
 * @param <C> the context the host gives its kind of extension
 * @param <F> the type the host reads its own fields of a descriptor into; {@link Void} for a host that reads none
 */
public final class ChassisHost<C extends ExtensionContext, F> implements Extensions {
	private final @NotNull HostOptions options;
	private final @NotNull System.Logger logger;
	private final @NotNull SelectionFile selection;
	private final @NotNull ServiceRegistry registry;
	private final @NotNull ExtensionEvents events;
	private final @NotNull ExtensionEntries<C, F> entries;
	private final @NotNull ExtensionSettlement<C, F> settlement;
	private final @NotNull ExtensionControl<C, F> control;

	/**
	 * Makes the host's extensions; nothing is read until {@link #start()}.
	 *
	 * @param options  how the host loads its extensions
	 * @param kind     the host's extension contract, such as {@code ArborModule}; a main class must implement it
	 * @param contexts makes the host's context of an extension from the one Chassis keeps
	 * @param factory  makes the instance of a main class
	 */
	public ChassisHost(
			@NotNull HostOptions options,
			@NotNull Class<? extends Extension<C>> kind,
			@NotNull ContextFactory<C> contexts,
			@NotNull ExtensionFactory factory
	) {
		this(null, options, kind, (base, fields) -> contexts.create(base), factory);
	}

	/**
	 * Makes the extensions of a host that reads fields of its own from each descriptor; nothing is read until
	 * {@link #start()}. The fields are read from the same file as the descriptor, into the host's type: fields Chassis
	 * knows and fields the type lacks are left out, and an extension whose fields cannot be read fails.
	 *
	 * @param options  how the host loads its extensions
	 * @param kind     the host's extension contract, such as {@code ArborModule}; a main class must implement it
	 * @param fields   the type the host reads its fields into, such as a record with an {@code updater} component
	 * @param contexts makes the host's context of an extension from the one Chassis keeps and the host's fields
	 * @param factory  makes the instance of a main class
	 */
	public ChassisHost(
			@NotNull HostOptions options,
			@NotNull Class<? extends Extension<C>> kind,
			@NotNull Class<F> fields,
			@NotNull FieldsContextFactory<C, F> contexts,
			@NotNull ExtensionFactory factory
	) {
		this(fields, options, kind, contexts, factory);
	}

	// The fields first: both public constructors end here, and a host that reads none passes no type.
	private ChassisHost(
			@Nullable Class<F> fields,
			HostOptions options,
			Class<? extends Extension<C>> kind,
			FieldsContextFactory<C, F> contexts,
			ExtensionFactory factory
	) {
		this.options = options;
		this.logger = options.getLoggers().apply(options.getKind());
		this.selection = new SelectionFile(options.getSelectionFile(), options.getKind());
		this.registry = new ServiceRegistry(error -> logger.log(System.Logger.Level.WARNING, "A service listener failed", error));
		this.events = new ExtensionEvents(options.getKind(), logger);
		this.entries = new ExtensionEntries<>(options.getKind(), events);

		ExtensionLauncher<C, F> launcher = new ExtensionLauncher<>(settings(options, kind), contexts, factory, registry, logger);
		DependencyResolver resolver = new DependencyResolver(options.getHostVersion(), options.getOffered(), options.getPlatform());
		ExtensionDiscovery<F> discovery = new ExtensionDiscovery<>(options.getFolder(), options.getDescriptorFile(), fields);
		this.settlement = new ExtensionSettlement<>(entries, resolver, launcher);
		this.control = new ExtensionControl<>(entries, discovery, launcher, settlement, events);
	}

	private static ExtensionLauncher.Settings settings(HostOptions options, Class<?> kind) {
		Path transformed = options.getTransformedFolder() == null ? options.getDataFolder().resolve(".transformed")
				: options.getTransformedFolder();
		JarTransformation transformation = new JarTransformation(options.getTransformers(), transformed);

		return new ExtensionLauncher.Settings(options.getKind(), kind, options.getDataFolder(), options.getExportLayer(),
				transformation, options.getPreparation(), options.getPlatform(), options.getLoggers());
	}

	/**
	 * Returns the host's own fields of an installed extension's descriptor, as the host names their type.
	 *
	 * @param id the extension's id
	 * @return the fields, or empty when no such extension is installed, its descriptor could not be read, or the host
	 * reads no fields
	 */
	public synchronized @NotNull Optional<F> fields(@NotNull String id) {
		return entries.fields(id);
	}

	/**
	 * Returns the services as the host sees them; what it registers here belongs to the host and stays until the host
	 * removes it.
	 *
	 * @return the host's services
	 */
	public @NotNull Services services() {
		return registry.as(options.getKind());
	}

	/**
	 * Finds the jars, reads which are turned off, and starts every extension that can start.
	 *
	 * @throws IOException when the folder or the selection file cannot be read
	 */
	public synchronized void start() throws IOException {
		control.install(selection.disabled());
		settlement.settle();
	}

	/**
	 * Stops every running extension, dependents first. They wait for the host to start again.
	 */
	public synchronized void stop() {
		control.stopAll(List.of("the " + options.getKind() + "s' host to start"));
	}

	/**
	 * Returns the class loader of a running extension, for diagnostics and tests such as a check that a stopped
	 * extension's loader is collected.
	 *
	 * @param id the extension's id
	 * @return its loader, or {@code null} when it does not run
	 */
	public synchronized @Nullable ClassLoader classLoader(@NotNull String id) {
		return entries.loader(id).orElse(null);
	}

	/**
	 * Returns the instance of a running extension's main class.
	 *
	 * @param id the extension's id
	 * @return the instance, or empty when it does not run
	 */
	public synchronized @NotNull Optional<Extension<C>> instance(@NotNull String id) {
		return entries.instance(id);
	}

	@Override
	public synchronized @NotNull List<InstalledExtension> list() {
		return entries.list();
	}

	@Override
	public synchronized @NotNull Optional<InstalledExtension> find(@NotNull String id) {
		return entries.find(id);
	}

	@Override
	public synchronized @NotNull InstalledExtension enable(@NotNull String id) throws ExtensionException {
		entries.require(id);
		changeSelection(id, false);

		return control.enable(id);
	}

	@Override
	public synchronized @NotNull InstalledExtension disable(@NotNull String id) throws ExtensionException {
		entries.require(id);
		changeSelection(id, true);

		return control.disable(id);
	}

	@Override
	public synchronized @NotNull InstalledExtension reload(@NotNull String id) throws ExtensionException {
		return control.reload(id);
	}

	@Override
	public synchronized @NotNull List<InstalledExtension> rescan() {
		try {
			control.install(selection.disabled());
		} catch (IOException e) {
			logger.log(System.Logger.Level.WARNING, "Could not look at " + options.getFolder() + " again", e);
		}

		control.revisit();

		return entries.list();
	}

	@Override
	public @NotNull AutoCloseable subscribe(@NotNull Consumer<ExtensionEvent> listener) {
		return events.subscribe(listener);
	}

	private void changeSelection(String id, boolean off) throws ExtensionException {
		try {
			Set<String> disabled = selection.disabled();
			boolean changed = off ? disabled.add(id) : disabled.remove(id);
			if (changed) selection.write(disabled);
		} catch (IOException e) {
			throw new ExtensionException(id, "Could not keep the choice for '" + id + "' in " + options.getSelectionFile() + ": " + e.getMessage(), e);
		}
	}
}
