package me.whereareiam.chassis.adapter.attache;

import org.jetbrains.annotations.NotNull;

/**
 * The library groups one extension declares in its Attache descriptor, which it loads once it knows it needs them,
 * such as the driver of the one database it is configured for. A host hands it to the extension through its own
 * context, from {@link AttacheLibraries#groups(String)}.
 */
@FunctionalInterface
public interface LibraryGroups {
	/**
	 * Loads the libraries of the given groups into the extension's class loader. A library already loaded, or one the
	 * extension's loader already sees, is skipped, so a group may be asked for more than once.
	 *
	 * @param groups the names of the groups
	 * @throws IllegalArgumentException when the extension declares no group of one of the names
	 * @throws IllegalStateException    when the extension does not run, or a library cannot be loaded
	 */
	void load(@NotNull String... groups);
}
