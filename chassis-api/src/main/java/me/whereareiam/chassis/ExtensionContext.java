package me.whereareiam.chassis;

import me.whereareiam.chassis.library.ExtensionLibraries;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.service.Services;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * What a host gives an extension for one load. Hosts extend it with what their kind of extension may use, such as
 * configuration and translations.
 */
public interface ExtensionContext {
	/**
	 * Returns the descriptor the extension was loaded from.
	 *
	 * @return the extension's descriptor
	 */
	@NotNull ExtensionDescriptor descriptor();

	/**
	 * Returns the extension's own folder for its files, {@code <host's data folder>/<id>}. The host creates it before
	 * {@link Extension#enable(ExtensionContext)}; it is kept across loads.
	 *
	 * @return the extension's data folder
	 */
	@NotNull Path dataFolder();

	/**
	 * Returns a logger named after the extension.
	 *
	 * @return the extension's logger
	 */
	@NotNull System.Logger logger();

	/**
	 * Returns the services, seen as this extension: what it registers here belongs to it and is removed when it stops,
	 * and so are its watches.
	 *
	 * @return the services as this extension
	 */
	@NotNull Services services();

	/**
	 * Returns the libraries the extension declares, to load its groups. The host's {@link
	 * me.whereareiam.chassis.library.LibraryLoader} loaded the others before the main class.
	 *
	 * @return the extension's libraries
	 */
	@NotNull ExtensionLibraries libraries();

	/**
	 * Hands a resource to the host, which closes it when the extension stops, after {@link Extension#disable()}. Resources
	 * close in the reverse order they were handed over; a failure to close one does not keep the others open.
	 *
	 * @param resource what to close when the extension stops
	 */
	void onClose(@NotNull AutoCloseable resource);
}
