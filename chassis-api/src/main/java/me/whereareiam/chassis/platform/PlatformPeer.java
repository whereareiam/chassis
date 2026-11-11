package me.whereareiam.chassis.platform;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A peer: software the platform has loaded beside the host, such as a Bukkit or Velocity plugin or a NeoForge mod,
 * as Chassis needs to know it for descriptors' {@code peers}.
 */
@Getter
@Builder
@ToString
public final class PlatformPeer {
	/**
	 * The id the platform knows it by.
	 */
	private final @NotNull String id;
	/**
	 * Its version as it states it, compared with a descriptor's range once it reads as a version; {@code null} when it
	 * states none.
	 */
	private final @Nullable String version;
	/**
	 * The loader of its classes, where the extensions that name its packages load those packages from; {@code null}
	 * on platforms where every peer shares the host's loader, such as NeoForge.
	 */
	@ToString.Exclude
	private final @Nullable ClassLoader classLoader;
}
