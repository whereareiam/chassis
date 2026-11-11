package me.whereareiam.chassis.common.loading;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * One extension's class loader: its jar, over the export layer, the exported classes of the extensions it depends on,
 * and the packages it names of the platform's peers. A class is looked for in the export layer first, then in the
 * packages it names of each peer, then in the exported packages of each dependency, then in the jar and the jars a
 * preparation added, so an extension cannot replace a class the host, a peer it names or a dependency provides, and
 * sees of a dependency only what that dependency exports.
 */
public final class ExtensionClassLoader extends URLClassLoader {
	static {
		registerAsParallelCapable();
	}

	private final @NotNull List<String> exports;
	private final @NotNull List<ExtensionClassLoader> dependencies;
	private final @NotNull List<PeerPackages> peers;

	/**
	 * Makes the loader.
	 *
	 * @param id           the extension's id, which names the loader
	 * @param jar          the extension's jar
	 * @param layer        the host's export layer
	 * @param exports      the packages the extension exports to its dependents, each with its subpackages
	 * @param dependencies the loaders of the extensions it depends on that are enabled
	 * @param peers        the packages it names of the platform's peers that are loaded, each with the peer's loader
	 */
	public ExtensionClassLoader(
			@NotNull String id,
			@NotNull URL jar,
			@NotNull ExportLayer layer,
			@NotNull List<String> exports,
			@NotNull List<ExtensionClassLoader> dependencies,
			@NotNull List<PeerPackages> peers
	) {
		super("chassis-extension:" + id, new URL[]{jar}, layer);
		this.exports = exports.stream().map(name -> name + ".").toList();
		this.dependencies = List.copyOf(dependencies);
		this.peers = List.copyOf(peers);
	}

	/**
	 * Adds a jar after the extension's own, such as a library a preparation loads for it.
	 *
	 * @param jar the jar to add
	 */
	public void addJar(@NotNull Path jar) {
		try {
			addURL(jar.toUri().toURL());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(jar + " cannot be read as a class path entry", e);
		}
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> found = findLoadedClass(name);
			if (found == null) found = fromParent(name);
			// A package the extension names of a peer is the peer's, and a package a dependency exports is the
			// dependency's, as the host's exported packages are the host's: a copy in this jar is never used, so objects
			// of them pass between the two.
			if (found == null) found = fromPeers(name);
			if (found == null) found = fromDependencies(name);
			if (found == null) found = ownClass(name);
			if (found == null) throw new ClassNotFoundException(name);

			if (resolve) resolveClass(found);

			return found;
		}
	}

	// A class of an exported package of this jar, or of what a dependency exports, directly or through its own.
	private @Nullable Class<?> exportedClass(String name) {
		synchronized (getClassLoadingLock(name)) {
			Class<?> own = exports(name) ? ownClass(name) : null;
			if (own != null) return own;

			return fromDependencies(name);
		}
	}

	private @Nullable Class<?> ownClass(String name) {
		Class<?> loaded = findLoadedClass(name);
		if (loaded != null) return loaded;
		try {
			return findClass(name);
		} catch (ClassNotFoundException missing) {
			return null;
		}
	}

	private @Nullable Class<?> fromDependencies(String name) {
		for (ExtensionClassLoader dependency : dependencies) {
			Class<?> found = dependency.exportedClass(name);
			if (found != null) return found;
		}

		return null;
	}

	private @Nullable Class<?> fromParent(String name) {
		try {
			return getParent().loadClass(name);
		} catch (ClassNotFoundException notThere) {
			return null;
		}
	}

	private @Nullable Class<?> fromPeers(String name) {
		for (PeerPackages peer : peers) {
			if (!peer.names(name)) continue;
			try {
				return peer.loader().loadClass(name);
			} catch (ClassNotFoundException notThere) {
				// Another peer may hold the same package.
			}
		}

		return null;
	}

	/**
	 * Packages of a platform's peer an extension loads from that peer's own class loader.
	 *
	 * @param loader   the peer's class loader
	 * @param packages the packages, each with its subpackages
	 */
	public record PeerPackages(@NotNull ClassLoader loader, @NotNull List<String> packages) {
		/**
		 * Makes the packages.
		 *
		 * @param loader   the peer's class loader
		 * @param packages the packages, each with its subpackages
		 */
		public PeerPackages {
			packages = packages.stream().map(name -> name.endsWith(".") ? name : name + ".").toList();
		}

		private boolean names(String className) {
			for (String prefix : packages)
				if (className.startsWith(prefix)) return true;

			return false;
		}
	}

	private boolean exports(String name) {
		for (String prefix : exports)
			if (name.startsWith(prefix)) return true;

		return false;
	}
}
