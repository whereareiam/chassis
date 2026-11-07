package me.whereareiam.chassis;

import me.whereareiam.chassis.event.ExtensionEvent;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.InstalledExtension;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Controls a host's extensions while it runs: what is installed and in which state, and turning one on, off or loading
 * it again. A host may offer it to administrators, such as through commands.
 *
 * <p>Changes are made one at a time. Turning an extension off stops its enabled dependents first, which then wait for
 * it; turning it on starts it and every waiting extension that can now start. Neither touches the game content or the
 * host itself.</p>
 */
public interface Extensions {
	/**
	 * Lists every extension the host found, in the order they start.
	 *
	 * @return the installed extensions
	 */
	@NotNull List<InstalledExtension> list();

	/**
	 * Finds an installed extension.
	 *
	 * @param id the extension's id
	 * @return the extension, or empty when the host knows no extension of that id
	 */
	@NotNull Optional<InstalledExtension> find(@NotNull String id);

	/**
	 * Turns an extension on and keeps it on across restarts. Starts it when it can, then every waiting extension that
	 * can start now.
	 *
	 * @param id the extension's id
	 * @return the extension afterwards
	 * @throws ExtensionException when no such extension is installed
	 */
	@NotNull InstalledExtension enable(@NotNull String id) throws ExtensionException;

	/**
	 * Turns an extension off and keeps it off across restarts. Stops its enabled dependents first; they wait for it.
	 *
	 * @param id the extension's id
	 * @return the extension afterwards
	 * @throws ExtensionException when no such extension is installed
	 */
	@NotNull InstalledExtension disable(@NotNull String id) throws ExtensionException;

	/**
	 * Loads an extension again from its jar, which may have been replaced: stops it and its enabled dependents, reads
	 * the jar anew in a fresh class loader and starts them again.
	 *
	 * @param id the extension's id
	 * @return the extension afterwards
	 * @throws ExtensionException when no such extension is installed
	 */
	@NotNull InstalledExtension reload(@NotNull String id) throws ExtensionException;

	/**
	 * Looks at the host's folder again: installs jars added since, and starts what can start now, such as an extension
	 * that waited for a plugin the platform has loaded since. Extensions that run stay as they are.
	 *
	 * @return every installed extension afterwards
	 */
	@NotNull List<InstalledExtension> rescan();

	/**
	 * Follows every change of state from now on. A listener runs on the thread that made the change.
	 *
	 * @param listener receives each change
	 * @return closes the subscription
	 */
	@NotNull AutoCloseable subscribe(@NotNull Consumer<ExtensionEvent> listener);
}
