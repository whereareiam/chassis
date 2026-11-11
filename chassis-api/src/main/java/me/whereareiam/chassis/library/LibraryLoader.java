package me.whereareiam.chassis.library;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Loads the libraries an extension declares into its class loader, before its main class loads. A host passes one in
 * its options, such as {@code chassis-adapter-attache}'s; without one, extensions bring what they need in their jars.
 */
@FunctionalInterface
public interface LibraryLoader {
	/**
	 * The loader of hosts that load no libraries: an extension gets an {@link ExtensionLibraries} without groups.
	 */
	LibraryLoader NONE = target -> groups -> {
		throw new IllegalArgumentException("'" + target.descriptor().getId() + "' has no library group " + Arrays.toString(groups)
				+ "; the host loads no libraries");
	};

	/**
	 * Loads the libraries the extension declares outside any group into its class loader.
	 *
	 * @param target the extension
	 * @return the extension's libraries, through which it loads its groups
	 * @throws Exception when a library cannot be loaded; the extension then fails to start
	 */
	@NotNull ExtensionLibraries load(@NotNull LibraryTarget target) throws Exception;
}
