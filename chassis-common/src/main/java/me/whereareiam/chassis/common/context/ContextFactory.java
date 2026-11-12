package me.whereareiam.chassis.common.context;

import me.whereareiam.chassis.ExtensionContext;
import org.jetbrains.annotations.NotNull;

/**
 * Makes the host's context of an extension from the one Chassis keeps, which tracks what the extension registers;
 * the host's context hands its methods to that one and adds its own.
 *
 * @param <C> the host's context
 */
@FunctionalInterface
public interface ContextFactory<C extends ExtensionContext> {
	/**
	 * Makes the context of one load.
	 *
	 * @param base the context Chassis keeps for the load
	 * @return the host's context
	 * @throws Exception when the context cannot be made; the extension then fails to start
	 */
	@NotNull C create(@NotNull ExtensionContext base) throws Exception;
}
