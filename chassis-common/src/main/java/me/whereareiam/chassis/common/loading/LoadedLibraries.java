package me.whereareiam.chassis.common.loading;

import me.whereareiam.chassis.library.LibraryTarget;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The extension a host's library loader loads for, which keeps what the loader handed over to close it before the
 * extension's class loader closes.
 */
public final class LoadedLibraries implements LibraryTarget {
	private final @NotNull ExtensionDescriptor descriptor;
	private final @NotNull Path jar;
	private final @NotNull ExtensionClassLoader loader;
	private final Deque<AutoCloseable> resources = new ArrayDeque<>();
	private boolean closed;

	public LoadedLibraries(@NotNull ExtensionDescriptor descriptor, @NotNull Path jar, @NotNull ExtensionClassLoader loader) {
		this.descriptor = descriptor;
		this.jar = jar;
		this.loader = loader;
	}

	@Override
	public @NotNull ExtensionDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public @NotNull Path jar() {
		return jar;
	}

	@Override
	public @NotNull ClassLoader classLoader() {
		return loader;
	}

	@Override
	public void add(@NotNull Path library) {
		loader.addPath(library);
	}

	@Override
	public synchronized void onClose(@NotNull AutoCloseable resource) {
		if (closed) throw new IllegalStateException(descriptor.getId() + " has stopped; its libraries can no longer hand over resources");

		resources.push(resource);
	}

	/**
	 * Closes what the library loader handed over, the last first.
	 *
	 * @return the first failure to close one, with the later ones suppressed, or {@code null}
	 */
	public synchronized Exception close() {
		closed = true;
		Exception failure = null;
		while (!resources.isEmpty()) {
			try {
				resources.pop().close();
			} catch (Exception e) {
				if (failure == null) failure = e;
				else failure.addSuppressed(e);
			}
		}

		return failure;
	}
}
