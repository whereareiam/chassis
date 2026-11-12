package me.whereareiam.chassis.common.context;

import me.whereareiam.chassis.ExtensionContext;
import org.jetbrains.annotations.NotNull;

/**
 * Makes the host's context of an extension from the one Chassis keeps and the host's own fields of the extension's
 * descriptor; the host decides what of its fields the extension sees.
 *
 * @param <C> the host's context
 * @param <F> the type the host reads its fields into
 */
@FunctionalInterface
public interface FieldsContextFactory<C extends ExtensionContext, F> {
	/**
	 * Makes the context of one load.
	 *
	 * @param base   the context Chassis keeps for the load
	 * @param fields the host's fields of the extension's descriptor
	 * @return the host's context
	 * @throws Exception when the context cannot be made; the extension then fails to start
	 */
	@NotNull C create(@NotNull ExtensionContext base, F fields) throws Exception;
}
