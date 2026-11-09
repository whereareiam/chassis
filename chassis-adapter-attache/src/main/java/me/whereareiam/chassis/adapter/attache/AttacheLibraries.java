package me.whereareiam.chassis.adapter.attache;

import lombok.Builder;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.VerbosityMode;
import me.whereareiam.chassis.ExtensionPreparation;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Loads the libraries an extension declares in its Attache descriptor into the extension's own class loader, before
 * its main class loads. Only the descriptor inside the extension's jar is read, never the host's. A library the
 * extension's loader already sees is skipped; one only the extension declares stays its own, invisible to the host and
 * to the extensions that depend on it, and goes with its loader when the extension stops.
 *
 * <pre>{@code
 * HostOptions options = HostOptions.builder()
 *         ...
 *         .preparation(AttacheLibraries.builder()
 *                 .folder(dataFolder.resolve("libraries"))
 *                 .build())
 *         .build();
 * }</pre>
 *
 * <p>An extension whose libraries cannot all be loaded fails to start, with Attache's reason, which names the library,
 * as its failure.
 * Declare only libraries the host does not export: the classes the host exports always win over an extension's
 * own.</p>
 */
@Builder
public final class AttacheLibraries implements ExtensionPreparation {
	/**
	 * The folder downloaded libraries are kept in, shared by every extension of the host.
	 */
	private final @NotNull Path folder;
	/**
	 * Where Attache logs. Defaults to the {@link System.Logger} named {@code attache}.
	 */
	@Builder.Default
	private final @NotNull LoggingHelper logging = new SystemLogging(System.getLogger("attache"));
	/**
	 * How much Attache logs about each extension's libraries. Defaults to {@link VerbosityMode#SUMMARY}.
	 */
	@Builder.Default
	private final @NotNull VerbosityMode verbosity = VerbosityMode.SUMMARY;

	@Override
	public @NotNull AutoCloseable prepare(@NotNull Loader loader) throws Exception {
		ExtensionLibraryManager libraries = new ExtensionLibraryManager(logging, folder, loader);
		try {
			libraries.setVerbosityMode(verbosity);
			libraries.setDescriptorSources(List.of(loader.jar().toUri().toURL()));
			libraries.loadDescriptors();
		} catch (Exception e) {
			closeAfter(libraries, e);
			throw e;
		}

		return libraries;
	}

	private static void closeAfter(ExtensionLibraryManager libraries, Exception failure) {
		try {
			libraries.close();
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
		}
	}
}
