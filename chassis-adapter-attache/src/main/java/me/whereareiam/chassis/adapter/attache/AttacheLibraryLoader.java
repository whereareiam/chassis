package me.whereareiam.chassis.adapter.attache;

import me.whereareiam.attache.LibraryManager;
import me.whereareiam.attache.platform.standalone.StandaloneLibraryManager;
import me.whereareiam.chassis.library.ExtensionLibraries;
import me.whereareiam.chassis.library.LibraryLoader;
import me.whereareiam.chassis.library.LibraryTarget;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Loads the libraries an extension declares with Attache's Gradle plugin into the extension's own class loader. Each
 * extension gets a library manager of its own that reads only its jar's descriptors, with the host's repositories, log
 * and download folder:
 *
 * <pre>{@code
 * HostOptions.builder()
 *         // ...
 *         .libraries(new AttacheLibraryLoader(hostLibraries))
 *         .build();
 * }</pre>
 *
 * <p>A library the host's class loaders already provide is skipped, so an extension that declares a library the host
 * exports uses the host's.</p>
 */
public final class AttacheLibraryLoader implements LibraryLoader {
	private final @NotNull LibraryManager host;

	/**
	 * Makes the loader.
	 *
	 * @param host the host's own library manager, whose repositories, log, verbosity and download folder extensions
	 *             share
	 */
	public AttacheLibraryLoader(@NotNull LibraryManager host) {
		this.host = host;
	}

	@Override
	public @NotNull ExtensionLibraries load(@NotNull LibraryTarget target) throws Exception {
		Path downloads = host.getSaveDirectory();
		StandaloneLibraryManager libraries = new StandaloneLibraryManager(host.getLoggingHelper(), downloads.getParent(),
				downloads.getFileName().toString(), target.classLoader());
		target.onClose(libraries);

		libraries.setVerbosityMode(host.getVerbosityMode());
		host.getRepositories().forEach(libraries::addRepository);
		libraries.setDescriptorSources(List.of(target.jar().toUri().toURL()));
		libraries.loadDescriptors();

		String id = target.descriptor().getId();

		return groups -> {
			try {
				libraries.loadGroups(groups);
			} catch (IllegalArgumentException undeclared) {
				// Attache names the groups the extension does declare.
				throw undeclared;
			} catch (RuntimeException failure) {
				throw new IllegalStateException("'" + id + "' could not load the library groups " + String.join(", ", groups)
						+ ": " + failure.getMessage(), failure);
			}
		};
	}
}
