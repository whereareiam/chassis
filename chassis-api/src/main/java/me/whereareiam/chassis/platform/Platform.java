package me.whereareiam.chassis.platform;

import org.jetbrains.annotations.NotNull;

/**
 * The platform a host runs on, as Chassis needs to know it: its name for descriptors' {@code platforms}, and which of
 * its plugins or mods are loaded, for descriptors' {@code plugins}. {@code chassis-platform} has one per platform.
 */
public interface Platform {
	/**
	 * Returns the platform's name, lowercase, such as {@code neoforge} or {@code paper}.
	 *
	 * @return the name
	 */
	@NotNull String name();

	/**
	 * Tells whether a plugin or mod is loaded.
	 *
	 * @param id the id the platform knows it by
	 * @return whether it is loaded now
	 */
	boolean hasPlugin(@NotNull String id);
}
