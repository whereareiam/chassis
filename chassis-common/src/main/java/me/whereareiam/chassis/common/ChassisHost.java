package me.whereareiam.chassis.common;

import me.whereareiam.chassis.Extension;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.ExtensionPreparation;
import me.whereareiam.chassis.Extensions;
import me.whereareiam.chassis.common.discovery.ExtensionDiscovery;
import me.whereareiam.chassis.common.lifecycle.LoadedContext;
import me.whereareiam.chassis.common.loading.ExtensionClassLoader;
import me.whereareiam.chassis.common.resolution.DependencyResolver;
import me.whereareiam.chassis.common.service.ServiceRegistry;
import me.whereareiam.chassis.event.ExtensionEvent;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.platform.PlatformPeer;
import me.whereareiam.chassis.service.Services;
import me.whereareiam.chassis.type.ExtensionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A host's extensions: it finds their jars, decides what can start, loads each in a class loader of its own over the
 * host's export layer, starts and stops them in the order of their dependencies, and removes what each registered when
 * it stops. It is the {@link Extensions} a host hands to administrators.
 *
 * <pre>{@code
 * ChassisHost<ModuleContext, Void> modules = new ChassisHost<>(options, ArborModule.class, base -> new ArborModuleContext(base, ...), factory);
 * modules.start();
 * ...
 * modules.stop();
 * }</pre>
 *
 * <p>Every change is made under one lock, on the caller's thread; a host calls it from the thread it starts and stops
 * extensions on.</p>
 *
 * <p>It logs what a host's administrator needs to act on: an extension that waits, with what for, one that cannot or
 * did not start, with why, and one that did not stop cleanly. Each start and stop of an extension is a debug line; a
 * host says in its own words what it started and stopped, from {@link #list()}.</p>
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
	private final @NotNull Class<?> kind;
	private final @NotNull FieldsContextFactory<C, F> contexts;
	private final @NotNull ExtensionFactory factory;
	private final @NotNull System.Logger logger;
	private final @NotNull ExtensionDiscovery<F> discovery;
	private final @NotNull DependencyResolver resolver;
	private final @NotNull SelectionFile selection;
	private final @NotNull ServiceRegistry registry;
	private final List<Consumer<ExtensionEvent>> listeners = new CopyOnWriteArrayList<>();

	// Every installed extension by id, in the order its jar was found; started ones also in start order.
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final List<String> started = new ArrayList<>();
	// While the host stops, every extension waits for it to start again; that is no news worth a line each.
	private boolean stopping;

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
		this.kind = kind;
		this.contexts = contexts;
		this.factory = factory;
		this.logger = options.getLoggers().apply(options.getKind());
		this.discovery = new ExtensionDiscovery<>(options.getFolder(), options.getDescriptorFile(), fields);
		this.resolver = new DependencyResolver(options.getHostVersion(), options.getOffered(), options.getPlatform());
		this.selection = new SelectionFile(options.getSelectionFile(), options.getKind());
		this.registry = new ServiceRegistry(error -> logger.log(System.Logger.Level.WARNING, "A service listener failed", error));
	}

	/**
	 * Returns the host's own fields of an installed extension's descriptor, as the host names their type.
	 *
	 * @param id the extension's id
	 * @return the fields, or empty when no such extension is installed, its descriptor could not be read, or the host
	 * reads no fields
	 */
	public synchronized @NotNull Optional<F> fields(@NotNull String id) {
		Entry entry = entries.get(id);

		return entry == null ? Optional.empty() : Optional.ofNullable(entry.found.fields());
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
		Set<String> disabled = selection.disabled();
		for (ExtensionDiscovery.Found<F> found : discovery.discover()) install(found, disabled);
		settle();
	}

	/**
	 * Stops every running extension, dependents first. They wait for the host to start again.
	 */
	public synchronized void stop() {
		stopping = true;
		try {
			for (String id : List.copyOf(started).reversed())
				stopOne(entries.get(id), ExtensionState.WAITING, List.of("the " + options.getKind() + "s' host to start"));
		} finally {
			stopping = false;
		}
	}

	/**
	 * Returns the class loader of a running extension, for diagnostics and tests such as a check that a stopped
	 * extension's loader is collected.
	 *
	 * @param id the extension's id
	 * @return its loader, or {@code null} when it does not run
	 */
	public synchronized @Nullable ClassLoader classLoader(@NotNull String id) {
		Entry entry = entries.get(id);

		return entry == null || entry.loaded == null ? null : entry.loaded.loader;
	}

	/**
	 * Returns the instance of a running extension's main class.
	 *
	 * @param id the extension's id
	 * @return the instance, or empty when it does not run
	 */
	public synchronized @NotNull Optional<Extension<C>> instance(@NotNull String id) {
		Entry entry = entries.get(id);

		return entry == null || entry.loaded == null ? Optional.empty() : Optional.of(entry.loaded.instance);
	}

	@Override
	public synchronized @NotNull List<InstalledExtension> list() {
		List<InstalledExtension> listed = new ArrayList<>();
		for (String id : started) listed.add(entries.get(id).view());
		for (Entry entry : entries.values())
			if (!started.contains(entry.id())) listed.add(entry.view());

		return listed;
	}

	@Override
	public synchronized @NotNull Optional<InstalledExtension> find(@NotNull String id) {
		Entry entry = entries.get(id);

		return entry == null ? Optional.empty() : Optional.of(entry.view());
	}

	@Override
	public synchronized @NotNull InstalledExtension enable(@NotNull String id) throws ExtensionException {
		Entry entry = entry(id);
		changeSelection(id, false);
		if (entry.state == ExtensionState.ENABLED) return entry.view();

		if (entry.found.descriptor() == null) entry.found = discovery.read(entry.found.jar());
		entry.candidate();
		settle();

		return entry.view();
	}

	@Override
	public synchronized @NotNull InstalledExtension disable(@NotNull String id) throws ExtensionException {
		Entry entry = entry(id);
		changeSelection(id, true);
		stopDependents(id, "disabled");
		if (entry.state == ExtensionState.ENABLED) stopOne(entry, ExtensionState.DISABLED, List.of());
		else change(entry, ExtensionState.DISABLED, List.of(), null);
		settle();

		return entry.view();
	}

	@Override
	public synchronized @NotNull InstalledExtension reload(@NotNull String id) throws ExtensionException {
		Entry entry = entry(id);
		List<Entry> dependents = stopDependents(id, "reloading");
		if (entry.state == ExtensionState.ENABLED) stopOne(entry, ExtensionState.WAITING, List.of("its own reload"));

		if (!Files.isRegularFile(entry.found.jar())) {
			remove(entry);
			throw new ExtensionException(id, entry.found.jar().getFileName() + " is gone; '" + id + "' is no longer installed");
		}

		ExtensionDiscovery.Found<F> reread = discovery.read(entry.found.jar());
		if (reread.descriptor() != null && !reread.id().equals(id)) {
			remove(entry);
			install(reread, Set.of());
			settle();
			throw new ExtensionException(id, entry.found.jar().getFileName() + " is now the extension '" + reread.id() + "'");
		}
		entry.found = reread;
		if (entry.state != ExtensionState.DISABLED) entry.candidate();
		dependents.forEach(Entry::candidate);
		settle();

		return entry.view();
	}

	@Override
	public synchronized @NotNull List<InstalledExtension> rescan() {
		try {
			Set<String> disabled = selection.disabled();
			Set<Path> known = new LinkedHashSet<>();
			entries.values().forEach(entry -> known.add(entry.found.jar()));
			for (ExtensionDiscovery.Found<F> found : discovery.discover())
				if (!known.contains(found.jar())) install(found, disabled);
		} catch (IOException e) {
			logger.log(System.Logger.Level.WARNING, "Could not look at " + options.getFolder() + " again", e);
		}

		for (Entry entry : List.copyOf(entries.values())) {
			if (entry.state == ExtensionState.ENABLED) continue;
			if (!Files.isRegularFile(entry.found.jar())) {
				remove(entry);
				continue;
			}
			if (entry.found.descriptor() == null && entry.state == ExtensionState.FAILED) {
				entry.found = discovery.read(entry.found.jar());
				if (entry.found.descriptor() != null) entry.candidate();
				else change(entry, ExtensionState.FAILED, List.of(), entry.found.failure());
			}
			if (entry.state == ExtensionState.WAITING) entry.candidate();
		}
		settle();

		return list();
	}

	@Override
	public @NotNull AutoCloseable subscribe(@NotNull Consumer<ExtensionEvent> listener) {
		listeners.add(listener);

		return () -> listeners.remove(listener);
	}

	private Entry entry(String id) throws ExtensionException {
		Entry entry = entries.get(id);
		if (entry == null) throw new ExtensionException(id, "No " + options.getKind() + " '" + id + "' is installed");

		return entry;
	}

	private void install(ExtensionDiscovery.Found<F> found, Set<String> disabled) {
		String id = found.id();
		if (entries.containsKey(id)) {
			found = ExtensionDiscovery.Found.failed(found.jar(), new ExtensionException(id,
					"Both " + entries.get(id).found.jar().getFileName() + " and " + found.jar().getFileName() + " are '" + id + "'; " + found.jar().getFileName() + " is left out"));
			id = found.id();
			if (entries.containsKey(id)) return;
		}

		Entry entry = new Entry(found);
		entries.put(id, entry);
		if (found.descriptor() == null) change(entry, ExtensionState.FAILED, List.of(), found.failure());
		else if (disabled.contains(id)) change(entry, ExtensionState.DISABLED, List.of(), null);
	}

	private void remove(Entry entry) {
		entries.remove(entry.id());
		started.remove(entry.id());
	}

	// Starts what can start now. A failure to start leaves the extensions that need it waiting, which the next pass
	// decides, so it repeats until a pass starts nothing.
	private void settle() {
		boolean progressed = true;
		while (progressed) {
			progressed = false;

			Map<String, ExtensionDescriptor> candidates = new LinkedHashMap<>();
			Map<String, ExtensionDescriptor> running = new LinkedHashMap<>();
			Map<String, String> unavailable = new LinkedHashMap<>();
			for (Entry entry : entries.values()) {
				ExtensionDescriptor descriptor = entry.found.descriptor();
				if (entry.state == ExtensionState.ENABLED) running.put(entry.id(), descriptor);
				else if (entry.state == null || entry.state == ExtensionState.WAITING) candidates.put(entry.id(), descriptor);
				else unavailable.put(entry.id(), entry.state == ExtensionState.DISABLED ? "disabled" : "failed");
			}
			if (candidates.isEmpty()) return;

			DependencyResolver.Resolution resolution = resolver.resolve(candidates, unavailable, running);
			resolution.failed().forEach((id, reason) -> change(entries.get(id), ExtensionState.FAILED, List.of(), new ExtensionException(id, reason)));
			resolution.waiting().forEach((id, reasons) -> change(entries.get(id), ExtensionState.WAITING, reasons, null));

			for (String id : resolution.order()) {
				Entry entry = entries.get(id);
				List<String> missing = missingDependencies(entry);
				if (!missing.isEmpty()) {
					change(entry, ExtensionState.WAITING, missing, null);
					continue;
				}
				if (startOne(entry)) progressed = true;
			}
		}
	}

	private List<String> missingDependencies(Entry entry) {
		List<String> missing = new ArrayList<>();
		for (ExtensionDescriptor.Dependency dependency : entry.found.descriptor().getDependencies()) {
			Entry target = entries.get(dependency.getId());
			if (dependency.isOptional() && (target == null || target.state != ExtensionState.ENABLED)) continue;
			if (target == null || target.state != ExtensionState.ENABLED)
				missing.add("'" + dependency.getId() + "' " + dependency.getVersion() + " (" + (target == null || target.state == null ? "not installed" : target.state.name().toLowerCase()) + ")");
		}

		return missing;
	}

	@SuppressWarnings("unchecked")
	private boolean startOne(Entry entry) {
		ExtensionDescriptor descriptor = entry.found.descriptor();
		List<ExtensionClassLoader> dependencies = new ArrayList<>();
		for (ExtensionDescriptor.Dependency dependency : descriptor.getDependencies()) {
			Entry target = entries.get(dependency.getId());
			if (target != null && target.loaded != null) dependencies.add(target.loaded.loader);
		}

		ExtensionClassLoader loader = null;
		AutoCloseable prepared = null;
		LoadedContext base = null;
		try {
			List<ExtensionClassLoader.PeerPackages> peers = new ArrayList<>();
			for (ExtensionDescriptor.PeerDependency peer : descriptor.getPeers()) {
				if (peer.getPackages().isEmpty()) continue;
				options.getPlatform().peer(peer.getId())
						.map(PlatformPeer::getClassLoader)
						.ifPresent(classLoader -> peers.add(new ExtensionClassLoader.PeerPackages(classLoader, peer.getPackages())));
			}
			loader = new ExtensionClassLoader(entry.id(), entry.found.jar().toUri().toURL(), options.getExportLayer(), descriptor.getExports(), dependencies,
					peers);
			prepared = options.getPreparation().prepare(new PreparedLoader(descriptor, entry.found.jar(), loader));
			Class<?> main = Class.forName(descriptor.getMain(), true, loader);
			if (!kind.isAssignableFrom(main))
				throw new ExtensionException(entry.id(), descriptor.getMain() + " is no " + kind.getName());

			Path dataFolder = Files.createDirectories(options.getDataFolder().resolve(entry.id()));
			base = new LoadedContext(descriptor, dataFolder, options.getLoggers().apply(entry.id()), registry);
			C context = contexts.create(base, entry.found.fields());
			Extension<C> instance = (Extension<C>) factory.create(main, context);
			instance.enable(context);

			entry.loaded = new Loaded(loader, prepared, instance, base);
			started.add(entry.id());
			change(entry, ExtensionState.ENABLED, List.of(), null);
			logger.log(System.Logger.Level.DEBUG, "Enabled the " + options.getKind() + " " + descriptor.getId() + " " + descriptor.getVersion());

			return true;
		} catch (Throwable failure) {
			if (failure instanceof VirtualMachineError error) throw error;

			if (base != null) {
				Exception cleanup = base.close();
				if (cleanup != null) failure.addSuppressed(cleanup);
			}
			closePrepared(prepared, failure);
			closeLoader(loader, failure);
			ExtensionException reported = failure instanceof ExtensionException known ? known
					: new ExtensionException(entry.id(), "'" + entry.id() + "' failed to start: " + failure, detached(failure));
			change(entry, ExtensionState.FAILED, List.of(), reported);
			logger.log(System.Logger.Level.ERROR, "The " + options.getKind() + " " + entry.id() + " failed to start", reported);

			return false;
		}
	}

	// A copy of a failure that refers to no class of an extension, kept while the extension is failed. A throwable
	// keeps the classes of the frames it was made in, and may be of a type of the extension's own jar, so the original
	// would keep the extension's class loader until it is enabled again. The copy keeps the type's name, the message
	// and the stack trace as text.
	private static Throwable detached(Throwable failure) {
		Throwable copy = new RuntimeException(failure.getClass().getName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
		copy.setStackTrace(failure.getStackTrace());
		if (failure.getCause() != null && failure.getCause() != failure) copy.initCause(detached(failure.getCause()));
		for (Throwable suppressed : failure.getSuppressed()) copy.addSuppressed(detached(suppressed));

		return copy;
	}

	// Stops the enabled extensions that depend on the given one, directly or not, the last started first. Each waits
	// for the given one by the word for what happens to it, and for the other stopped ones as waiting, as the resolver
	// would say it.
	private List<Entry> stopDependents(String id, String word) {
		Set<String> affected = new LinkedHashSet<>(List.of(id));
		boolean grew = true;
		while (grew) {
			grew = false;
			for (String candidate : started) {
				if (affected.contains(candidate)) continue;
				boolean depends = entries.get(candidate).found.descriptor().getDependencies().stream()
						.anyMatch(dependency -> affected.contains(dependency.getId()));
				if (depends) grew = affected.add(candidate);
			}
		}
		affected.remove(id);

		List<Entry> stopped = new ArrayList<>();
		for (String candidate : List.copyOf(started).reversed()) {
			if (!affected.contains(candidate)) continue;

			Entry entry = entries.get(candidate);
			List<String> reasons = new ArrayList<>();
			for (ExtensionDescriptor.Dependency dependency : entry.found.descriptor().getDependencies()) {
				if (dependency.getId().equals(id))
					reasons.add("'" + id + "' " + dependency.getVersion() + " (" + word + ")");
				else if (affected.contains(dependency.getId()))
					reasons.add("'" + dependency.getId() + "' " + dependency.getVersion() + " (waiting)");
			}
			stopOne(entry, ExtensionState.WAITING, reasons);
			stopped.add(entry);
		}

		return stopped;
	}

	private void stopOne(Entry entry, ExtensionState next, List<String> waitingFor) {
		Loaded loaded = entry.loaded;
		if (loaded == null) return;

		Throwable failure = null;
		try {
			loaded.instance.disable();
		} catch (Throwable e) {
			if (e instanceof VirtualMachineError error) throw error;
			failure = e;
		}
		Exception cleanup = loaded.context.close();
		if (failure == null) failure = cleanup;
		else if (cleanup != null) failure.addSuppressed(cleanup);
		failure = closePrepared(loaded.prepared, failure);
		if (failure != null) logger.log(System.Logger.Level.WARNING, "The " + options.getKind() + " " + entry.id() + " did not stop cleanly", failure);
		closeLoader(loaded.loader, null);

		entry.loaded = null;
		started.remove(entry.id());
		change(entry, next, waitingFor, null);
		logger.log(System.Logger.Level.DEBUG, "Stopped the " + options.getKind() + " " + entry.id());
	}

	// Closes what the preparation of a load returned; a failure is added to the given one, or becomes it.
	private static @Nullable Throwable closePrepared(@Nullable AutoCloseable prepared, @Nullable Throwable failure) {
		if (prepared == null) return failure;
		try {
			prepared.close();
		} catch (Exception e) {
			if (failure == null) return e;
			failure.addSuppressed(e);
		}

		return failure;
	}

	private void closeLoader(@Nullable ExtensionClassLoader loader, @Nullable Throwable failure) {
		if (loader == null) return;
		try {
			loader.close();
		} catch (IOException e) {
			if (failure != null) failure.addSuppressed(e);
			else logger.log(System.Logger.Level.WARNING, "Could not close the class loader " + loader.getName(), e);
		}
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

	private void change(Entry entry, ExtensionState next, List<String> waitingFor, @Nullable Throwable failure) {
		ExtensionState previous = entry.state;
		boolean same = previous == next && entry.waitingFor.equals(waitingFor);
		entry.state = next;
		entry.waitingFor = List.copyOf(waitingFor);
		entry.failure = failure;
		if (same) return;

		if (next == ExtensionState.WAITING && !waitingFor.isEmpty() && !stopping)
			logger.log(System.Logger.Level.INFO, "The " + options.getKind() + " " + entry.id() + " waits for " + String.join(", ", waitingFor));
		else if (next == ExtensionState.FAILED && failure != null && previous != ExtensionState.ENABLED && loadedFailure(failure))
			logger.log(System.Logger.Level.WARNING, "The " + options.getKind() + " " + entry.id() + " cannot start: " + failure.getMessage());

		ExtensionEvent event = ExtensionEvent.builder().extension(entry.view()).previous(previous).build();
		for (Consumer<ExtensionEvent> listener : listeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException e) {
				logger.log(System.Logger.Level.WARNING, "A listener of the " + options.getKind() + "s failed", e);
			}
		}
	}

	// A failure to start is logged where it happens, with its stack; the others (unreadable jar, unfit, cycle) here.
	private static boolean loadedFailure(Throwable failure) {
		return !(failure instanceof ExtensionException known) || !known.getMessage().contains("failed to start");
	}

	/**
	 * Makes the host's context of an extension from the one Chassis keeps, which tracks what the extension registers;
	 * the host's context hands its methods to that one and adds its own.
	 *
	 * @param <C> the host's context
	 */
	@FunctionalInterface
	public interface ContextFactory<C extends ExtensionContext> {
		/**
		 * Makes the context of one load.
		 *
		 * @param base the context Chassis keeps for the load
		 * @return the host's context
		 * @throws Exception when the context cannot be made; the extension then fails to start
		 */
		@NotNull C create(@NotNull ExtensionContext base) throws Exception;
	}

	/**
	 * Makes the host's context of an extension from the one Chassis keeps and the host's own fields of the extension's
	 * descriptor; the host decides what of its fields the extension sees.
	 *
	 * @param <C> the host's context
	 * @param <F> the type the host reads its fields into
	 */
	@FunctionalInterface
	public interface FieldsContextFactory<C extends ExtensionContext, F> {
		/**
		 * Makes the context of one load.
		 *
		 * @param base   the context Chassis keeps for the load
		 * @param fields the host's fields of the extension's descriptor
		 * @return the host's context
		 * @throws Exception when the context cannot be made; the extension then fails to start
		 */
		@NotNull C create(@NotNull ExtensionContext base, F fields) throws Exception;
	}

	private final class Entry {
		private ExtensionDiscovery.Found<F> found;
		// null: installed and not yet decided.
		private ExtensionState state;
		private List<String> waitingFor = List.of();
		private Throwable failure;
		private Loaded loaded;

		private Entry(ExtensionDiscovery.Found<F> found) {
			this.found = found;
		}

		private String id() {
			return found.id();
		}

		private void candidate() {
			state = null;
			waitingFor = List.of();
			failure = null;
		}

		private InstalledExtension view() {
			return InstalledExtension.builder()
					.id(id())
					.descriptor(found.descriptor())
					.jar(found.jar())
					.state(state == null ? ExtensionState.WAITING : state)
					.waitingFor(waitingFor)
					.failure(failure)
					.build();
		}
	}

	private final class Loaded {
		private final ExtensionClassLoader loader;
		private final AutoCloseable prepared;
		private final Extension<C> instance;
		private final LoadedContext context;

		private Loaded(ExtensionClassLoader loader, AutoCloseable prepared, Extension<C> instance, LoadedContext context) {
			this.loader = loader;
			this.prepared = prepared;
			this.instance = instance;
			this.context = context;
		}
	}

	private record PreparedLoader(
			@NotNull ExtensionDescriptor descriptor,
			@NotNull Path jar,
			@NotNull ExtensionClassLoader loader
	) implements ExtensionPreparation.Loader {
		@Override
		public @NotNull ClassLoader classLoader() {
			return loader;
		}

		@Override
		public void addJar(@NotNull Path jar) {
			loader.addJar(jar);
		}
	}
}
