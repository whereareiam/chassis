package me.whereareiam.chassis.adapter.guice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.ChassisHost;
import me.whereareiam.chassis.common.HostOptions;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.testing.ExtensionJar;
import me.whereareiam.chassis.testing.LeakCheck;
import me.whereareiam.chassis.type.ExtensionState;
import org.example.guice.InjectedModule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiceExtensionFactoryTest {
	@TempDir
	Path folder;

	@Test
	void anExtensionGetsTheExportedServicesAndItsContextAndIsCollectedOnceStopped() throws Exception {
		ExtensionJar.of("guice-module.yml", """
				id: injected
				version: 1.0.0
				main: org.example.guice.InjectedModule
				""").with(InjectedModule.class).write(folder.resolve("modules/injected.jar"));
		Injector hostInjector = Guice.createInjector(binder -> binder.bind(Clock.class).toInstance(() -> 42));
		ChassisHost<ExtensionContext> host = new ChassisHost<>(options(), GuiceModule.class, base -> base,
				GuiceExtensionFactory.builder(hostInjector, ExtensionContext.class).export(Clock.class).build());

		host.start();
		assertEquals(ExtensionState.ENABLED, host.find("injected").orElseThrow().getState());
		assertEquals("injected 42 hello", System.getProperty("chassis.test.guice"));

		WeakReference<ClassLoader> loader = new WeakReference<>(host.classLoader("injected"));
		host.disable("injected");

		assertTrue(LeakCheck.collected(loader, Duration.ofSeconds(10)), "the host's injector kept the extension");
		assertTrue(hostInjector.getAllBindings().keySet().stream().noneMatch(key -> key.getTypeLiteral().getRawType().getName().startsWith("org.example")));
	}

	private HostOptions options() {
		return HostOptions.builder()
				.kind("module")
				.folder(folder.resolve("modules"))
				.descriptorFile("guice-module.yml")
				.dataFolder(folder.resolve("data"))
				.selectionFile(folder.resolve("modules.yml"))
				.hostVersion(Version.parse("1.0.0"))
				.platform(new Platform() {
					@Override
					public @NotNull String name() {
						return "test";
					}

					@Override
					public boolean hasPlugin(@NotNull String id) {
						return false;
					}
				})
				.exportLayer(new ExportLayer(getClass().getClassLoader(), List.of("me.whereareiam.chassis", "jakarta.inject")))
				.build();
	}
}
