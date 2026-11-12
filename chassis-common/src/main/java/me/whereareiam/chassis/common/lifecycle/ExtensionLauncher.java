package me.whereareiam.chassis.common.lifecycle;

import lombok.RequiredArgsConstructor;
import me.whereareiam.chassis.Extension;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.ExtensionPreparation;
import me.whereareiam.chassis.common.context.FieldsContextFactory;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.common.loading.ExtensionClassLoader;
import me.whereareiam.chassis.common.service.ServiceRegistry;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Starts and stops one extension: its class loader, the host's preparation of it, its main class, its context and
 * instance. What a start made is undone when the start fails, and when the extension stops. It keeps no state of its
 * own: {@link ExtensionSettlement} decides what starts, {@link ExtensionControl} what stops, and
 * {@link ExtensionEntries} keeps what runs.
 *
 * @param <C> the host's context
 * @param <F> the type the host reads its own fields of a descriptor into
 */
@RequiredArgsConstructor
public final class ExtensionLauncher<C extends ExtensionContext, F> {
	private final @NotNull Settings settings;
	private final @NotNull FieldsContextFactory<C, F> contexts;
	private final @NotNull ExtensionFactory factory;
	private final @NotNull ServiceRegistry registry;
	private final @NotNull System.Logger logger;

	/**
	 * Starts an extension whose dependencies run.
	 *
	 * @param jar          the extension's jar
	 * @param descriptor   its descriptor
	 * @param fields       the host's fields of its descriptor
	 * @param dependencies the loaders of the dependencies that run
	 * @return what runs
	 * @throws ExtensionException when it does not start; what the start made is undone, and the failure refers to no
	 *                            class of the extension
	 */
	@NotNull Running<C> start(
			@NotNull Path jar,
			@NotNull ExtensionDescriptor descriptor,
			@Nullable F fields,
			@NotNull List<ExtensionClassLoader> dependencies
	) throws ExtensionException {
		String id = descriptor.getId();
		ExtensionClassLoader loader = null;
		AutoCloseable prepared = null;
		LoadedContext base = null;
		try {
			loader = new ExtensionClassLoader(id, jar.toUri().toURL(), settings.exportLayer(), descriptor.getExports(), dependencies,
					peers(descriptor));
			prepared = settings.preparation().prepare(new PreparedLoader(descriptor, jar, loader));
			Class<?> main = mainClass(descriptor, loader);

			Path dataFolder = Files.createDirectories(settings.dataFolder().resolve(id));
			base = new LoadedContext(descriptor, dataFolder, settings.loggers().apply(id), registry);
			Extension<C> instance = enabled(main, base, fields);
			logger.log(System.Logger.Level.DEBUG, "Enabled the " + settings.kind() + " " + id + " " + descriptor.getVersion());

			return new Running<>(loader, prepared, instance, base);
		} catch (Throwable failure) {
			if (failure instanceof VirtualMachineError error) throw error;

			LoadCleanup.closeAll(failure, LoadCleanup.closing(base), prepared, loader);
			ExtensionException reported = failure instanceof ExtensionException known ? known
					: new ExtensionException(id, "'" + id + "' failed to start: " + failure, LoadCleanup.detached(failure));
			logger.log(System.Logger.Level.ERROR, "The " + settings.kind() + " " + id + " failed to start", reported);

			throw reported;
		}
	}

	/**
	 * Stops a running extension: disables it, closes what its context tracks, what the preparation returned and its
	 * loader. A failure on the way is logged; the rest is still closed.
	 *
	 * @param id      the extension's id
	 * @param running what runs
	 */
	void stop(@NotNull String id, @NotNull Running<C> running) {
		Throwable failure = disable(running.instance());
		failure = LoadCleanup.closeAll(failure, LoadCleanup.closing(running.context()), running.prepared(), running.loader());
		if (failure != null) logger.log(System.Logger.Level.WARNING, "The " + settings.kind() + " " + id + " did not stop cleanly", failure);

		logger.log(System.Logger.Level.DEBUG, "Stopped the " + settings.kind() + " " + id);
	}

	private Class<?> mainClass(ExtensionDescriptor descriptor, ExtensionClassLoader loader) throws ClassNotFoundException, ExtensionException {
		Class<?> main = Class.forName(descriptor.getMain(), true, loader);
		if (settings.contract().isAssignableFrom(main)) return main;

		throw new ExtensionException(descriptor.getId(), descriptor.getMain() + " is no " + settings.contract().getName());
	}

	@SuppressWarnings("unchecked")
	private Extension<C> enabled(Class<?> main, LoadedContext base, @Nullable F fields) throws Exception {
		C context = contexts.create(base, fields);
		Extension<C> instance = (Extension<C>) factory.create(main, context);
		instance.enable(context);

		return instance;
	}

	// The failure of disable, or null when it returned.
	private static @Nullable Throwable disable(Extension<?> instance) {
		try {
			instance.disable();

			return null;
		} catch (Throwable failure) {
			if (failure instanceof VirtualMachineError error) throw error;

			return failure;
		}
	}

	// The packages the extension names of each loaded peer that has a class loader of its own.
	private List<ExtensionClassLoader.PeerPackages> peers(ExtensionDescriptor descriptor) {
		List<ExtensionClassLoader.PeerPackages> peers = new ArrayList<>();
		for (ExtensionDescriptor.PeerDependency peer : descriptor.getPeers()) {
			if (peer.getPackages().isEmpty()) continue;

			settings.platform().peer(peer.getId())
					.map(PlatformPeer::getClassLoader)
					.ifPresent(classLoader -> peers.add(new ExtensionClassLoader.PeerPackages(classLoader, peer.getPackages())));
		}

		return peers;
	}

	/**
	 * What the launcher takes of the host's options.
	 *
	 * @param kind        the host's word for its extensions, for log lines
	 * @param contract    the host's extension contract; a main class must implement it
	 * @param dataFolder  the folder that holds each extension's own folder
	 * @param exportLayer the packages extensions see of the host
	 * @param preparation prepares each extension's class loader before its main class loads
	 * @param platform    the platform, for the class loaders of the peers an extension names packages of
	 * @param loggers     gives the logger of an extension's id
	 */
	public record Settings(
			@NotNull String kind,
			@NotNull Class<?> contract,
			@NotNull Path dataFolder,
			@NotNull ExportLayer exportLayer,
			@NotNull ExtensionPreparation preparation,
			@NotNull Platform platform,
			@NotNull Function<String, System.Logger> loggers
	) {
	}

	/**
	 * A running extension, as its start made it.
	 *
	 * @param loader   its class loader
	 * @param prepared what the host's preparation returned, closed when it stops
	 * @param instance the instance of its main class
	 * @param context  the context Chassis keeps, which tracks what it registers
	 * @param <C>      the host's context
	 */
	record Running<C extends ExtensionContext>(
			@NotNull ExtensionClassLoader loader,
			@Nullable AutoCloseable prepared,
			@NotNull Extension<C> instance,
			@NotNull LoadedContext context
	) {
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
