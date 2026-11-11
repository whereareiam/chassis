package me.whereareiam.chassis.platform.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import me.whereareiam.chassis.platform.PlatformPeer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.ProxyVersion;
import me.whereareiam.chassis.model.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityPlatformTest {
	@Test
	void theVersionIsTheProxysRelease() {
		VelocityPlatform platform = new VelocityPlatform(proxy("4.3.1-SNAPSHOT (git-1a2b3c4d-b512)", Set.of()));

		assertEquals("velocity", platform.name());
		assertEquals(Version.parse("4.3.1"), platform.version());
		assertNull(VelocityPlatform.release("unknown"));
	}

	@Test
	void aPluginCountsOnceItIsLoaded() {
		VelocityPlatform platform = new VelocityPlatform(proxy("3.4.0", Set.of("luckperms")));

		PlatformPeer luckPerms = platform.peer("luckperms").orElseThrow();
		assertEquals("5.4.1", luckPerms.getVersion());
		assertSame(VelocityPlatformTest.class.getClassLoader(), luckPerms.getClassLoader());
		assertTrue(platform.peer("vault").isEmpty());
	}

	private static PluginContainer container(String id) {
		PluginDescription description = (PluginDescription) Proxy.newProxyInstance(PluginDescription.class.getClassLoader(), new Class<?>[]{PluginDescription.class},
				(instance, method, args) -> switch (method.getName()) {
					case "getId" -> id;
					case "getVersion" -> Optional.of("5.4.1");
					default -> null;
				});

		return (PluginContainer) Proxy.newProxyInstance(PluginContainer.class.getClassLoader(), new Class<?>[]{PluginContainer.class},
				(instance, method, args) -> switch (method.getName()) {
					case "getDescription" -> description;
					case "getInstance" -> Optional.of(new VelocityPlatformTest());
					default -> null;
				});
	}

	private static ProxyServer proxy(String version, Set<String> plugins) {
		PluginManager manager = (PluginManager) Proxy.newProxyInstance(PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class},
				(instance, method, args) -> method.getName().equals("getPlugin") ? Optional.ofNullable(plugins.contains((String) args[0]) ? container((String) args[0]) : null) : null);
		ProxyVersion proxyVersion = new ProxyVersion("Velocity", "Velocity Contributors", version);

		return (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(), new Class<?>[]{ProxyServer.class},
				(instance, method, args) -> switch (method.getName()) {
					case "getVersion" -> proxyVersion;
					case "getPluginManager" -> manager;
					default -> null;
				});
	}
}
