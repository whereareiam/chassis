package me.whereareiam.chassis.adapter.attache;

import lombok.Builder;
import lombok.Singular;
import me.whereareiam.attache.LibraryTransformer;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.VerbosityMode;
import me.whereareiam.chassis.ExtensionPreparation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>A host whose own libraries go through Attache {@link LibraryTransformer}s, such as one that downgrades their
 * bytecode for an older Java, hands the same transformers to the builder, so every extension's libraries pass them too:
 * {@code AttacheLibraries.builder().folder(...).transformers(hostLibraries.getLibraryTransformers()).build()}.</p>
 *
 * <p>Libraries an extension declares in a group are loaded only when it asks for them, through the
 * {@link LibraryGroups} that {@link #groups(String)} returns and its host hands to it.</p>
 *
 * <p>An extension whose libraries cannot all be loaded fails to start, with Attache's reason, which names the library,
 * as its failure.
 * Declare only libraries the host does not export: the classes the host exports always win over an extension's
 * own.</p>
 */
public final class AttacheLibraries implements ExtensionPreparation {
	private final @NotNull Path folder;
	private final @NotNull LoggingHelper logging;
	private final @NotNull VerbosityMode verbosity;
	private final @NotNull List<String> repositories;
	private final @NotNull List<LibraryTransformer> transformers;
	// The library manager of each running extension, by id, for its groups.
	private final Map<String, ExtensionLibraryManager> running = new ConcurrentHashMap<>();

	/**
	 * Makes the preparation.
	 *
	 * @param folder       the folder downloaded libraries are kept in, shared by every extension of the host
	 * @param logging      where Attache logs; defaults to the {@link System.Logger} named {@code attache}
	 * @param verbosity    how much Attache logs about each extension's libraries; defaults to
	 *                     {@link VerbosityMode#SUMMARY}
	 * @param repositories repositories every extension's libraries may come from, besides those its descriptor names,
	 *                     such as the host's own
	 * @param transformers transformers registered, in this order, on the library manager of every extension, which
	 *                     run on each library it downloads before the library loads, such as the transformers of the
	 *                     host's own manager; their results are kept in {@code folder}
	 */
	@Builder
	private AttacheLibraries(
			@NotNull Path folder,
			@Nullable LoggingHelper logging,
			@Nullable VerbosityMode verbosity,
			@Singular @NotNull List<String> repositories,
			@Singular @NotNull List<LibraryTransformer> transformers
	) {
		this.folder = folder;
		this.logging = logging == null ? new SystemLogging(System.getLogger("attache")) : logging;
		this.verbosity = verbosity == null ? VerbosityMode.SUMMARY : verbosity;
		this.repositories = List.copyOf(repositories);
		this.transformers = List.copyOf(transformers);
	}

	@Override
	public @NotNull AutoCloseable prepare(@NotNull Loader loader) throws Exception {
		String id = loader.descriptor().getId();
		ExtensionLibraryManager libraries = new ExtensionLibraryManager(logging, folder, loader);
		try {
			libraries.setVerbosityMode(verbosity);
			repositories.forEach(libraries::addRepository);
			transformers.forEach(libraries::registerLibraryTransformer);
			libraries.setDescriptorSources(List.of(loader.jar().toUri().toURL()));
			libraries.loadDescriptors();
		} catch (Exception e) {
			closeAfter(libraries, e);
			throw e;
		}
		running.put(id, libraries);

		return () -> {
			running.remove(id, libraries);
			libraries.close();
		};
	}

	/**
	 * Returns the library groups of an extension, which the host hands to it through its context. The groups reach
	 * the extension's current load: after a reload, the same object loads into the new one.
	 *
	 * <pre>{@code
	 * ChassisHost<ModuleContext, Void> modules = new ChassisHost<>(options, ExampleModule.class,
	 *         base -> new HostModuleContext(base, libraries.groups(base.descriptor().getId())), factory);
	 * }</pre>
	 *
	 * @param id the extension's id
	 * @return its groups; loading one throws {@link IllegalStateException} while the extension does not run
	 */
	public @NotNull LibraryGroups groups(@NotNull String id) {
		return groups -> {
			ExtensionLibraryManager libraries = running.get(id);
			if (libraries == null)
				throw new IllegalStateException("'" + id + "' does not run, so it cannot load the library groups " + String.join(", ", groups));
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

	private static void closeAfter(ExtensionLibraryManager libraries, Exception failure) {
		try {
			libraries.close();
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
		}
	}
}
