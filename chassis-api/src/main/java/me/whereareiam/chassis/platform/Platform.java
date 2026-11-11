package me.whereareiam.chassis.platform;

import me.whereareiam.chassis.model.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The platform a host runs on, as Chassis needs to know it: its name and version for descriptors' {@code platforms},
 * and which of its plugins or mods are loaded, for descriptors' {@code plugins}. {@code chassis-platform} has one per
 * platform.
 */
public interface Platform {
	/**
	 * Returns the platform's name, lowercase, such as {@code neoforge} or {@code paper}.
	 *
	 * @return the name
	 */
	@NotNull String name();

	/**
	 * Returns the platform's version, which the ranges of descriptors' {@code platforms} are compared with: the game's
	 * version on a game server or mod loader, such as {@code 1.21.4}, and the proxy's own on a proxy. A platform that
	 * knows none returns {@code null}; only entries without a range then accept it.
	 *
	 * @return the version, or {@code null}
	 */
	default @Nullable Version version() {
		return null;
	}

	/**
	 * Tells whether a plugin or mod is loaded.
	 *
	 * @param id the id the platform knows it by
	 * @return whether it is loaded now
	 */
	boolean hasPlugin(@NotNull String id);
}
