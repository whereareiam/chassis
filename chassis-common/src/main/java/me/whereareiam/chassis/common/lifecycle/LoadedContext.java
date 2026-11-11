package me.whereareiam.chassis.common.lifecycle;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.service.ServiceRegistry;
import me.whereareiam.chassis.library.ExtensionLibraries;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.service.Services;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The context of one load of an extension, which keeps what the extension registered through it and removes all of it
 * when the extension stops.
 */
public final class LoadedContext implements ExtensionContext {
	private final @NotNull ExtensionDescriptor descriptor;
	private final @NotNull Path dataFolder;
	private final @NotNull System.Logger logger;
	private final @NotNull ServiceRegistry registry;
	private final @NotNull Services services;
	private final @NotNull ExtensionLibraries libraries;
	private final Deque<AutoCloseable> resources = new ArrayDeque<>();
	private boolean closed;

	public LoadedContext(
			@NotNull ExtensionDescriptor descriptor,
			@NotNull Path dataFolder,
			@NotNull System.Logger logger,
			@NotNull ServiceRegistry registry,
			@NotNull ExtensionLibraries libraries
	) {
		this.descriptor = descriptor;
		this.dataFolder = dataFolder;
		this.logger = logger;
		this.registry = registry;
		this.services = registry.as(descriptor.getId());
		this.libraries = libraries;
	}

	@Override
	public @NotNull ExtensionDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public @NotNull Path dataFolder() {
		return dataFolder;
	}

	@Override
	public @NotNull System.Logger logger() {
		return logger;
	}

	@Override
	public @NotNull Services services() {
		return services;
	}

	@Override
	public @NotNull ExtensionLibraries libraries() {
		return libraries;
	}

	@Override
	public synchronized void onClose(@NotNull AutoCloseable resource) {
		if (closed) throw new IllegalStateException(descriptor.getId() + " has stopped; it can no longer hand over resources");

		resources.push(resource);
	}

	/**
	 * Closes every resource handed over, the last one first, then removes the extension's services and watches.
	 *
	 * @return the first failure to close a resource, with the later ones suppressed, or {@code null}
	 */
	public Exception close() {
		Exception failure = null;
		synchronized (this) {
			closed = true;
			while (!resources.isEmpty()) {
				try {
					resources.pop().close();
				} catch (Exception e) {
					if (failure == null) failure = e;
					else failure.addSuppressed(e);
				}
			}
		}
		registry.removeOwner(descriptor.getId());

		return failure;
	}
}
