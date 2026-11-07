package me.whereareiam.chassis;

import org.jetbrains.annotations.NotNull;

/**
 * Makes the instance of an extension's main class. The default makes it through its public constructor without
 * parameters; {@code chassis-adapter-guice} makes it through an injector of its own.
 */
public interface ExtensionFactory {
	/**
	 * The factory that calls the main class's public constructor without parameters.
	 */
	ExtensionFactory CONSTRUCTOR = new ExtensionFactory() {
		@Override
		public <T> @NotNull T create(@NotNull Class<T> main, @NotNull ExtensionContext context) throws Exception {
			return main.getConstructor().newInstance();
		}
	};

	/**
	 * Makes an instance of the main class for one load.
	 *
	 * @param main    the main class, loaded by the extension's own class loader
	 * @param context the context the extension is about to be enabled with
	 * @param <T>     the main class's type
	 * @return the instance to enable
	 * @throws Exception when the instance cannot be made; the extension then fails to load
	 */
	<T> @NotNull T create(@NotNull Class<T> main, @NotNull ExtensionContext context) throws Exception;
}
