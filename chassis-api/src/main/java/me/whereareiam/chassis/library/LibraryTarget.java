package me.whereareiam.chassis.library;

import me.whereareiam.chassis.model.ExtensionDescriptor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * The extension a {@link LibraryLoader} loads libraries for, between making its class loader and loading its main
 * class.
 */
public interface LibraryTarget {
	/**
	 * Returns the extension's descriptor.
	 *
	 * @return the descriptor
	 */
	@NotNull ExtensionDescriptor descriptor();

	/**
	 * Returns the extension's jar, which says which libraries it declares.
	 *
	 * @return the jar
	 */
	@NotNull Path jar();

	/**
	 * Returns the extension's class loader. It has a public {@code addPath(Path)} method, as {@link #add(Path)}, for
	 * library managers that look for one.
	 *
	 * @return the class loader
	 */
	@NotNull ClassLoader classLoader();

	/**
	 * Adds a library jar to the extension's class loader. Its classes are looked up with the extension's own, after the
	 * packages the host exports.
	 *
	 * @param library the jar
	 */
	void add(@NotNull Path library);

	/**
	 * Hands a resource of the loader to the host, which closes it when the extension stops, after the extension's own
	 * resources and before its class loader; and when the extension fails to start.
	 *
	 * @param resource what to close
	 */
	void onClose(@NotNull AutoCloseable resource);
}
