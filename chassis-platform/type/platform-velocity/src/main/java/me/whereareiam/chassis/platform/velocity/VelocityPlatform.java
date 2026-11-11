package me.whereareiam.chassis.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Velocity as Chassis sees it: named {@code velocity}, its version is the proxy's own, such as {@code 3.4.0}, and a
 * descriptor's {@code peers} are plugin ids, each with the version it states and the class loader of its instance. It
 * runs on Velocity 3 and 4.
 *
 * <pre>{@code
 * new VelocityPlatform(proxyServer)
 * }</pre>
 */
public final class VelocityPlatform implements Platform {
	private static final Pattern RELEASE = Pattern.compile("^(\\d+(?:\\.\\d+){0,2})");

	private final @NotNull ProxyServer proxy;
	private final @Nullable Version version;

	/**
	 * Describes the proxy a plugin runs on.
	 *
	 * @param proxy the proxy, as Velocity injects it into a plugin
	 */
	public VelocityPlatform(@NotNull ProxyServer proxy) {
		this.proxy = proxy;
		this.version = release(proxy.getVersion().getVersion());
	}

	@Override
	public @NotNull String name() {
		return "velocity";
	}

	@Override
	public @Nullable Version version() {
		return version;
	}

	@Override
	public @NotNull Optional<PlatformPeer> peer(@NotNull String id) {
		return proxy.getPluginManager().getPlugin(id).map(plugin -> PlatformPeer.builder()
				.id(plugin.getDescription().getId())
				.version(plugin.getDescription().getVersion().orElse(null))
				.classLoader(plugin.getInstance().map(instance -> instance.getClass().getClassLoader()).orElse(null))
				.build());
	}

	/**
	 * Reads the release a version of Velocity belongs to, such as {@code 3.4.0} from {@code 3.4.0-SNAPSHOT (git-1a2b3c4d)};
	 * a development build counts as its release, so a range such as {@code ^3.4} accepts it.
	 *
	 * @param version what Velocity reports as its version
	 * @return the release, or {@code null} when the version does not start with one
	 */
	static @Nullable Version release(@NotNull String version) {
		Matcher matcher = RELEASE.matcher(version.trim());

		return matcher.find() ? Version.parse(matcher.group(1)) : null;
	}
}
