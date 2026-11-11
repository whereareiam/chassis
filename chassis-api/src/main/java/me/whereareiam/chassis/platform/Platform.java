package me.whereareiam.chassis.platform;

import me.whereareiam.chassis.model.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The platform a host runs on, as Chassis needs to know it: its name and version for descriptors' {@code platforms},
 * and which peers it has loaded beside the host, such as plugins or mods, for descriptors' {@code peers}.
 * {@code chassis-platform} has one per platform.
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
	 * Finds a peer that is loaded now.
	 *
	 * @param id the id the platform knows it by
	 * @return the peer with its version and the loader of its classes, or empty when it is not loaded
	 */
	@NotNull Optional<PlatformPeer> peer(@NotNull String id);
}
