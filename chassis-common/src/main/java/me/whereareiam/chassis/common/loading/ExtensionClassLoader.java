package me.whereareiam.chassis.common.loading;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * One extension's class loader: its jar, over the export layer, and the exported classes of the extensions it depends
 * on. A class is looked for in the export layer first, then in the jar and the jars a preparation added, then in the
 * exported packages of each dependency's own jar, so an extension cannot replace a class the host exports and sees of
 * a dependency only what that dependency exports.
 */
public final class ExtensionClassLoader extends URLClassLoader {
	static {
		registerAsParallelCapable();
	}

	private final @NotNull List<String> exports;
	private final @NotNull List<ExtensionClassLoader> dependencies;

	/**
	 * Makes the loader.
	 *
	 * @param id           the extension's id, which names the loader
	 * @param jar          the extension's jar
	 * @param layer        the host's export layer
	 * @param exports      the packages the extension exports to its dependents, each with its subpackages
	 * @param dependencies the loaders of the extensions it depends on that are enabled
	 */
	public ExtensionClassLoader(
			@NotNull String id,
			@NotNull URL jar,
			@NotNull ExportLayer layer,
			@NotNull List<String> exports,
			@NotNull List<ExtensionClassLoader> dependencies
	) {
		super("chassis-extension:" + id, new URL[]{jar}, layer);
		this.exports = exports.stream().map(name -> name + ".").toList();
		this.dependencies = List.copyOf(dependencies);
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
			try {
				return super.loadClass(name, resolve);
			} catch (ClassNotFoundException missing) {
				Class<?> found = fromDependencies(name);
				if (found == null) throw missing;

				return found;
			}
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

	private boolean exports(String name) {
		for (String prefix : exports)
			if (name.startsWith(prefix)) return true;

		return false;
	}
}
