package me.whereareiam.chassis.platform.bukkit;

import me.whereareiam.chassis.model.Version;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitPlatformTest {
	@Test
	void theKindIsTheMostSpecificTheServerHasClassesOf() {
		assertEquals("folia", BukkitPlatform.kindOf(loaderWith("io.papermc.paper.threadedregions.RegionizedServer", "io.papermc.paper.ServerBuildInfo", "org.spigotmc.SpigotConfig")));
		assertEquals("paper", BukkitPlatform.kindOf(loaderWith("io.papermc.paper.ServerBuildInfo", "org.spigotmc.SpigotConfig")));
		assertEquals("paper", BukkitPlatform.kindOf(loaderWith("com.destroystokyo.paper.PaperConfig")));
		assertEquals("spigot", BukkitPlatform.kindOf(loaderWith("org.spigotmc.SpigotConfig")));
		assertEquals("bukkit", BukkitPlatform.kindOf(loaderWith()));
	}

	@Test
	void theVersionIsTheGames() {
		assertEquals(Version.parse("1.21.4"), BukkitPlatform.gameVersion("1.21.4-R0.1-SNAPSHOT"));
		assertEquals(Version.parse("26.1"), BukkitPlatform.gameVersion("26.1-R0.1-SNAPSHOT"));
		assertNull(BukkitPlatform.gameVersion("unknown"));
	}

	@Test
	void aPluginCountsOnceItIsEnabled() {
		BukkitPlatform platform = new BukkitPlatform(server(Map.of("LuckPerms", true, "Vault", false)), "paper", Version.parse("1.21.4"));

		assertTrue(platform.hasPlugin("LuckPerms"));
		assertFalse(platform.hasPlugin("Vault"));
		assertFalse(platform.hasPlugin("Missing"));
	}

	// A loader that has the given classes, answering each with some class, as a check of presence needs no more.
	private static ClassLoader loaderWith(String... names) {
		Set<String> present = Set.of(names);

		return new ClassLoader(null) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (present.contains(name)) return Object.class;

				throw new ClassNotFoundException(name);
			}
		};
	}

	private static Server server(Map<String, Boolean> plugins) {
		PluginManager manager = proxy(PluginManager.class, (method, args) -> {
			if (!method.equals("getPlugin")) return null;
			Boolean enabled = plugins.get((String) args[0]);

			return enabled == null ? null : proxy(Plugin.class, (pluginMethod, pluginArgs) -> pluginMethod.equals("isEnabled") ? enabled : null);
		});

		return proxy(Server.class, (method, args) -> method.equals("getPluginManager") ? manager : null);
	}

	private static <T> T proxy(Class<T> type, Answer answer) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> answer.answer(method.getName(), args)));
	}

	private interface Answer {
		Object answer(String method, Object[] args);
	}
}
