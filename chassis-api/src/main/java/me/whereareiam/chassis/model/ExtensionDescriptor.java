package me.whereareiam.chassis.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * What an extension jar says about itself in its descriptor file, the file its host names (such as
 * {@code arbor-module.yml}) at the root of the jar:
 *
 * <pre>{@code
 * id: monitoring
 * name: Monitoring
 * version: 1.0.0
 * main: net.oraylen.arbor.module.monitoring.MonitoringModule
 * description: Registers the server and reports it.
 * authors: [whereareiam]
 * host: ^1
 * requires:
 *   gateway: ^1
 *   fleet: ^1
 * dependencies:
 *   - id: other
 *     version: ^2
 *     optional: true
 * platforms:
 *   - name: neoforge
 *   - name: paper
 *     version: ">=1.21 <1.22 || ~26.1"
 * plugins: [luckperms]
 * }</pre>
 */
@Getter
@Builder
@ToString
@EqualsAndHashCode
public final class ExtensionDescriptor {
	/**
	 * The id: lowercase letters, digits and hyphens, starting with a letter; unique within a host.
	 */
	private final @NotNull String id;
	/**
	 * The name for people; the id when the descriptor names none.
	 */
	private final @NotNull String name;
	private final @NotNull Version version;
	/**
	 * The binary name of the main class.
	 */
	private final @NotNull String main;
	private final @Nullable String description;
	@Singular
	private final @NotNull List<String> authors;
	/**
	 * The versions of the host's API the extension is built for.
	 */
	@Builder.Default
	private final @NotNull VersionRange host = VersionRange.ANY;
	/**
	 * What the extension needs the host to offer, by the names the host gives them, such as Arbor's areas
	 * ({@code gateway: ^1}). A host starts nothing for an extension it does not offer.
	 */
	@Singular("require")
	private final @NotNull Map<String, VersionRange> requires;
	/**
	 * Other extensions of the same host it needs, or can use when they are there.
	 */
	@Singular
	private final @NotNull List<Dependency> dependencies;
	/**
	 * The platforms it runs on, such as {@code neoforge}, each with the versions of it accepted; empty means every
	 * platform the host runs on.
	 */
	@Singular
	private final @NotNull List<SupportedPlatform> platforms;
	/**
	 * Plugins or mods of the platform it needs, by the ids the platform knows them by. It waits until each is loaded.
	 */
	@Singular
	private final @NotNull List<String> plugins;

	/**
	 * A platform an extension runs on, with the versions of it accepted.
	 */
	@Getter
	@Builder
	@EqualsAndHashCode
	public static final class SupportedPlatform {
		/**
		 * The platform's name, lowercase, as {@link me.whereareiam.chassis.platform.Platform#name()} gives it.
		 */
		private final @NotNull String name;
		/**
		 * The versions of the platform accepted, compared with {@link me.whereareiam.chassis.platform.Platform#version()}.
		 */
		@Builder.Default
		private final @NotNull VersionRange version = VersionRange.ANY;

		/**
		 * Returns the platform as a descriptor names it, such as {@code paper ~26.1}.
		 *
		 * @return the name, followed by the range unless it accepts any version
		 */
		@Override
		public String toString() {
			return version.equals(VersionRange.ANY) ? name : name + " " + version;
		}
	}

	/**
	 * Another extension an extension needs, or can use.
	 */
	@Getter
	@Builder
	@ToString
	@EqualsAndHashCode
	public static final class Dependency {
		private final @NotNull String id;
		@Builder.Default
		private final @NotNull VersionRange version = VersionRange.ANY;
		/**
		 * Whether the extension also starts without it. When it is installed, it still starts first.
		 */
		private final boolean optional;
	}
}
