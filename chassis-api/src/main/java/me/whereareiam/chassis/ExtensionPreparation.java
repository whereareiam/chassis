package me.whereareiam.chassis;

import me.whereareiam.chassis.model.ExtensionDescriptor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Prepares an extension's class loader before its main class is loaded, such as by adding the libraries the extension
 * declares. The host runs it for each load, on the new loader, and closes what it returns when the extension stops or
 * fails to start, after the extension's own resources and before the loader.
 *
 * <pre>{@code
 * ExtensionPreparation libraries = loader -> {
 *     for (Path library : resolve(loader.descriptor()))
 *         loader.addJar(library);
 *     return () -> {};
 * };
 * }</pre>
 */
@FunctionalInterface
public interface ExtensionPreparation {
	/**
	 * The preparation that changes nothing.
	 */
	ExtensionPreparation NONE = loader -> () -> {
	};

	/**
	 * Prepares one load's class loader.
	 *
	 * @param loader the new loader, before the extension's main class is loaded
	 * @return what to close when this load ends; nothing to close is {@code () -> {}}
	 * @throws Exception when the loader cannot be prepared; the extension then fails to start
	 */
	@NotNull AutoCloseable prepare(@NotNull Loader loader) throws Exception;

	/**
	 * One load's class loader, as a preparation sees it.
	 */
	interface Loader {
		/**
		 * Returns the descriptor of the extension being loaded.
		 *
		 * @return the extension's descriptor
		 */
		@NotNull ExtensionDescriptor descriptor();

		/**
		 * Returns the extension's jar.
		 *
		 * @return the path of the jar the loader reads
		 */
		@NotNull Path jar();

		/**
		 * Returns the class loader itself, such as for reading resources of the jar.
		 *
		 * @return the extension's class loader
		 */
		@NotNull ClassLoader classLoader();

		/**
		 * Adds a jar to the loader, after the extension's own jar. The export layer still comes first, so a jar
		 * added here cannot replace a class the host exports.
		 *
		 * @param jar the jar to add
		 */
		void addJar(@NotNull Path jar);
	}
}
