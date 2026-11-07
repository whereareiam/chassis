package me.whereareiam.chassis.common.loading;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * The parent of every extension's class loader: the JDK, plus the packages the host exports from its own loader (its
 * API and the libraries it shares, unrelocated), and nothing else. An extension never sees the host's private
 * libraries or the platform, unless the host exports them.
 *
 * <p>A package is exported with its subpackages: exporting {@code net.kyori.adventure} exports
 * {@code net.kyori.adventure.text} too.</p>
 */
public final class ExportLayer extends ClassLoader {
	static {
		registerAsParallelCapable();
	}

	private final @NotNull ClassLoader host;
	private final @NotNull List<String> prefixes;
	private final @NotNull List<String> packages;

	/**
	 * Makes the layer.
	 *
	 * @param host     the loader the exported packages come from, usually the host's own
	 * @param packages the exported packages, such as {@code me.whereareiam.chassis}
	 */
	public ExportLayer(@NotNull ClassLoader host, @NotNull List<String> packages) {
		super("chassis-export-layer", ClassLoader.getPlatformClassLoader());
		this.host = host;
		this.packages = List.copyOf(packages);
		this.prefixes = packages.stream().map(name -> name.endsWith(".") ? name : name + ".").toList();
	}

	/**
	 * Returns the exported packages.
	 *
	 * @return the packages, as given
	 */
	public @NotNull List<String> packages() {
		return packages;
	}

	/**
	 * Tells whether a class or resource of the given binary or dotted name lies in an exported package.
	 *
	 * @param name a class name, or a resource path with slashes
	 * @return whether the layer hands it to the host
	 */
	public boolean exports(@NotNull String name) {
		String dotted = name.replace('/', '.');
		for (String prefix : prefixes)
			if (dotted.startsWith(prefix)) return true;

		return false;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (exports(name)) return host.loadClass(name);

		return super.loadClass(name, resolve);
	}

	@Override
	public URL getResource(String name) {
		if (exports(name)) return host.getResource(name);

		return super.getResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		if (exports(name)) return host.getResources(name);

		return Collections.emptyEnumeration();
	}
}
