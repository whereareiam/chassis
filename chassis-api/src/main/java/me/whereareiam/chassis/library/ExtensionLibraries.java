package me.whereareiam.chassis.library;

import org.jetbrains.annotations.NotNull;

/**
 * The libraries an extension declares, as the host's {@link LibraryLoader} loaded them into the extension's class
 * loader. The libraries it declares outside any group are there before its main class loads; a library that only some
 * installations need, such as the driver of one database, goes in a group, which the extension loads once it knows it
 * needs it:
 *
 * <pre>{@code
 * context.libraries().loadGroups(settings.database().toLowerCase(Locale.ROOT));
 * }</pre>
 *
 * <p>The libraries stay in the extension's class loader. Extensions that depend on it reach one only through the
 * packages it exports.</p>
 */
public interface ExtensionLibraries {
	/**
	 * Loads the libraries the extension declares in the given groups into its class loader. A library already loaded,
	 * or one the host provides, is skipped, so a group may be asked for more than once.
	 *
	 * @param groups the names of the groups
	 * @throws IllegalArgumentException when the extension declares no group of one of the names
	 * @throws IllegalStateException    when a library of the groups cannot be loaded
	 */
	void loadGroups(@NotNull String... groups);
}
