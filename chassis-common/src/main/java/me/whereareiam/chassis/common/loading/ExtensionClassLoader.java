package me.whereareiam.chassis.common.loading;

import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * One extension's class loader: its jar, over the export layer, and the classes of the extensions it depends on. A
 * class is looked for in the export layer first, then in the jar, then in each dependency's own jar, so an extension
 * cannot replace a class the host exports.
 */
public final class ExtensionClassLoader extends URLClassLoader {
	static {
		registerAsParallelCapable();
	}

	private final @NotNull List<ExtensionClassLoader> dependencies;

	/**
	 * Makes the loader.
	 *
	 * @param id           the extension's id, which names the loader
	 * @param jar          the extension's jar
	 * @param layer        the host's export layer
	 * @param dependencies the loaders of the extensions it depends on that are enabled
	 */
	public ExtensionClassLoader(
			@NotNull String id,
			@NotNull URL jar,
			@NotNull ExportLayer layer,
			@NotNull List<ExtensionClassLoader> dependencies
	) {
		super("chassis-extension:" + id, new URL[]{jar}, layer);
		this.dependencies = List.copyOf(dependencies);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			try {
				return super.loadClass(name, resolve);
			} catch (ClassNotFoundException missing) {
				for (ExtensionClassLoader dependency : dependencies) {
					Class<?> found = dependency.ownClass(name);
					if (found != null) return found;
				}
				throw missing;
			}
		}
	}

	private Class<?> ownClass(String name) {
		synchronized (getClassLoadingLock(name)) {
			Class<?> loaded = findLoadedClass(name);
			if (loaded != null) return loaded;
			try {
				return findClass(name);
			} catch (ClassNotFoundException missing) {
				for (ExtensionClassLoader dependency : dependencies) {
					Class<?> found = dependency.ownClass(name);
					if (found != null) return found;
				}

				return null;
			}
		}
	}
}
