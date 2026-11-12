package me.whereareiam.chassis.common.lifecycle;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.ExtensionPreparation;
import me.whereareiam.chassis.common.Greeting;
import me.whereareiam.chassis.common.TestModule;
import me.whereareiam.chassis.common.discovery.DescriptorReader;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.common.service.ServiceRegistry;
import me.whereareiam.chassis.exception.ExtensionException;
import me.whereareiam.chassis.model.ExtensionDescriptor;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
import me.whereareiam.chassis.service.ServiceKey;
import me.whereareiam.chassis.testing.ExtensionJar;
import org.example.failing.FailingModule;
import org.example.greeting.GreetingModule;
import org.example.greeting.Shared;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionLauncherTest {
	@TempDir
	Path folder;

	private final List<String> closed = new ArrayList<>();
	private final ServiceRegistry registry = new ServiceRegistry(error -> {
	});
	private ExtensionLauncher<ExtensionContext, Void> launcher;

	@BeforeEach
	void makeLauncher() {
		System.clearProperty("chassis.test.failing.closed");
		System.clearProperty("chassis.test.greeting.closed");
		ExtensionPreparation preparation = loader -> () -> closed.add(loader.descriptor().getId());
		launcher = new ExtensionLauncher<>(settings(preparation), (base, fields) -> base, ExtensionFactory.CONSTRUCTOR, registry,
				System.getLogger("test"));
	}

	@Test
	void aStartThatFailsUndoesWhatItMadeAndReportsAFailureWithoutTheExtensionsClasses() throws Exception {
		Path jar = ExtensionJar.of("test-module.yml", "").with(FailingModule.class).write(folder.resolve("failing.jar"));

		ExtensionException failure = assertThrows(ExtensionException.class,
				() -> launcher.start(jar, descriptor("failing", FailingModule.class), null, List.of()));

		assertTrue(failure.getMessage().startsWith("'failing' failed to start: java.lang.IllegalStateException"), failure.getMessage());
		assertEquals(RuntimeException.class, failure.getCause().getClass());
		assertEquals("true", System.getProperty("chassis.test.failing.closed"));
		assertEquals(List.of("failing"), closed);
		assertTrue(registry.as("host").find(ServiceKey.of(Greeting.class, "failing")).isEmpty());
	}

	@Test
	void aMainClassOfAnotherKindFailsBeforeItIsMade() throws Exception {
		Path jar = ExtensionJar.of("test-module.yml", "").with(Shared.class).write(folder.resolve("shared.jar"));

		ExtensionException failure = assertThrows(ExtensionException.class,
				() -> launcher.start(jar, descriptor("shared", Shared.class), null, List.of()));

		assertEquals(Shared.class.getName() + " is no " + TestModule.class.getName(), failure.getMessage());
		assertEquals(List.of("shared"), closed);
		assertFalse(Files.exists(folder.resolve("data/shared")));
	}

	@Test
	void aStopClosesWhatTheStartMade() throws Exception {
		Path jar = ExtensionJar.of("test-module.yml", "").with(GreetingModule.class, Shared.class).write(folder.resolve("greeting.jar"));

		ExtensionLauncher.Running<ExtensionContext> running = launcher.start(jar, descriptor("greeting", GreetingModule.class), null, List.of());
		assertTrue(Files.isDirectory(folder.resolve("data/greeting")));
		assertTrue(registry.as("host").find(ServiceKey.of(Greeting.class)).isPresent());

		launcher.stop("greeting", running);

		assertEquals("true", System.getProperty("chassis.test.greeting.closed"));
		assertEquals(List.of("greeting"), closed);
		assertTrue(registry.as("host").find(ServiceKey.of(Greeting.class)).isEmpty());
	}

	private static ExtensionDescriptor descriptor(String id, Class<?> main) throws Exception {
		String text = "id: " + id + "\nversion: 1.0.0\nmain: " + main.getName() + "\n";

		return new DescriptorReader().read(id, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
	}

	private ExtensionLauncher.Settings settings(ExtensionPreparation preparation) {
		Platform platform = new Platform() {
			@Override
			public @NotNull String name() {
				return "test";
			}

			@Override
			public @NotNull Optional<PlatformPeer> peer(@NotNull String id) {
				return Optional.empty();
			}
		};
		ExportLayer layer = new ExportLayer(getClass().getClassLoader(), List.of("me.whereareiam.chassis"));

		return new ExtensionLauncher.Settings("module", TestModule.class, folder.resolve("data"), layer, preparation, platform,
				System::getLogger);
	}
}
