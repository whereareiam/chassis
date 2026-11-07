package me.whereareiam.chassis;

import org.jetbrains.annotations.NotNull;

/**
 * What the main class of an extension jar implements. A host names its own kind of extension by extending this
 * contract with its context, such as {@code interface ArborModule extends Extension<ModuleContext>}, and loads only
 * main classes of that kind.
 *
 * <p>An extension starts with {@link #enable(ExtensionContext)} and stops with {@link #disable()}. Everything it
 * registers through its context (services, resources handed to {@link ExtensionContext#onClose(AutoCloseable)}) is
 * removed by the host after {@code disable}, and on a failed {@code enable} as well, so an extension releases only what
 * it made outside its context.</p>
 *
 * @param <C> the context the host gives this kind of extension
 */
public interface Extension<C extends ExtensionContext> {
	/**
	 * Starts the extension. The host calls it once per load, on the thread that enables extensions, after every
	 * dependency of the extension is enabled.
	 *
	 * @param context what the host gives the extension for this load
	 * @throws Exception when the extension cannot start; the host then removes what it registered, keeps it failed and
	 *                   leaves its dependents waiting
	 */
	void enable(@NotNull C context) throws Exception;

	/**
	 * Stops the extension before the host removes what it registered and closes its class loader. The host calls it
	 * after every enabled dependent of the extension is disabled.
	 *
	 * @throws Exception when stopping fails; the host still removes what the extension registered
	 */
	default void disable() throws Exception {
	}
}
