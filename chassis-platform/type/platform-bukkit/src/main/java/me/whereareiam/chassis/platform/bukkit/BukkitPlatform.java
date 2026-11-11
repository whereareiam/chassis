package me.whereareiam.chassis.platform.bukkit;

import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Bukkit server as Chassis sees it. Its name is the kind of server, the most specific of {@code folia},
 * {@code paper}, {@code spigot} and {@code bukkit}, so a descriptor that lists {@code paper} does not run on Folia
 * until it lists {@code folia} too. Its version is the game's, such as {@code 1.21.4}. A descriptor's
 * {@code peers} are plugin names, and a plugin counts once the server has enabled it; its version is the one its
 * {@code plugin.yml} states, and its classes are those of its own class loader.
 *
 * <pre>{@code
 * new BukkitPlatform(getServer())
 * }</pre>
 */
public final class BukkitPlatform implements Platform {
	// The kinds from the most specific, each with classes only that kind and its forks have.
	private static final List<Kind> KINDS = List.of(
			new Kind("folia", List.of("io.papermc.paper.threadedregions.RegionizedServer")),
			new Kind("paper", List.of("io.papermc.paper.ServerBuildInfo", "io.papermc.paper.configuration.Configuration",
					"com.destroystokyo.paper.PaperConfig")),
			new Kind("spigot", List.of("org.spigotmc.SpigotConfig"))
	);
	private static final Pattern GAME_VERSION = Pattern.compile("^(\\d+(?:\\.\\d+){0,2})");

	private final @NotNull Server server;
	private final @NotNull String name;
	private final @Nullable Version version;

	/**
	 * Describes the server a plugin runs on.
	 *
	 * @param server the server, such as {@code JavaPlugin.getServer()}
	 */
	public BukkitPlatform(@NotNull Server server) {
		this(server, kindOf(server.getClass().getClassLoader()), gameVersion(server.getBukkitVersion()));
	}

	BukkitPlatform(@NotNull Server server, @NotNull String name, @Nullable Version version) {
		this.server = server;
		this.name = name;
		this.version = version;
	}

	@Override
	public @NotNull String name() {
		return name;
	}

	@Override
	public @Nullable Version version() {
		return version;
	}

	@Override
	public @NotNull Optional<PlatformPeer> peer(@NotNull String id) {
		Plugin plugin = server.getPluginManager().getPlugin(id);
		if (plugin == null || !plugin.isEnabled()) return Optional.empty();

		return Optional.of(PlatformPeer.builder()
				.id(plugin.getName())
				.version(plugin.getDescription().getVersion())
				.classLoader(plugin.getClass().getClassLoader())
				.build());
	}

	/**
	 * Tells the kind of server from the classes its loader has.
	 *
	 * @param loader the loader of the server's classes
	 * @return {@code folia}, {@code paper}, {@code spigot} or {@code bukkit}
	 */
	static @NotNull String kindOf(@NotNull ClassLoader loader) {
		for (Kind kind : KINDS)
			for (String marker : kind.markers())
				if (has(loader, marker)) return kind.name();

		return "bukkit";
	}

	/**
	 * Reads the game's version from the server's Bukkit version, such as {@code 1.21.4} from
	 * {@code 1.21.4-R0.1-SNAPSHOT}.
	 *
	 * @param bukkitVersion what {@link Server#getBukkitVersion()} returns
	 * @return the game's version, or {@code null} when it does not start with one
	 */
	static @Nullable Version gameVersion(@NotNull String bukkitVersion) {
		Matcher matcher = GAME_VERSION.matcher(bukkitVersion.trim());

		return matcher.find() ? Version.parse(matcher.group(1)) : null;
	}

	private static boolean has(ClassLoader loader, String name) {
		try {
			loader.loadClass(name);

			return true;
		} catch (ClassNotFoundException | LinkageError absent) {
			return false;
		}
	}

	private record Kind(String name, List<String> markers) {
	}
}
