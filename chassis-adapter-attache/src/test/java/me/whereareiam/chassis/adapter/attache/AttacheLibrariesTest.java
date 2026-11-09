package me.whereareiam.chassis.adapter.attache;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.common.ChassisHost;
import me.whereareiam.chassis.common.HostOptions;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.testing.ExtensionJar;
import me.whereareiam.chassis.testing.LeakCheck;
import me.whereareiam.chassis.type.ExtensionState;
import me.whereareiam.attache.type.VerbosityMode;
import org.example.attache.LetteredModule;
import org.example.attache.library.Letters;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttacheLibrariesTest {
	private static final String DESCRIPTOR = "attache-module.yml";

	@TempDir
	Path folder;

	private ChassisHost<ExtensionContext> host;

	@BeforeEach
	void clearMarks() {
		System.clearProperty("chassis.test.attache");
	}

	@AfterEach
	void stopHost() {
		if (host != null) host.stop();
	}

	@Test
	void anExtensionUsesTheLibrariesItsDescriptorDeclaresAndIsCollectedOnceStopped() throws Exception {
		Path library = ExtensionJar.of("letters.txt", "").with(Letters.class).write(folder.resolve("source/letters-1.0.0.jar"));
		lettered(library.toUri().toString());

		start();

		assertEquals(ExtensionState.ENABLED, state());
		assertEquals("HELLO", System.getProperty("chassis.test.attache"));
		assertTrue(Files.exists(folder.resolve("libraries/org/example/letters/1.0.0/letters-1.0.0.jar")));

		WeakReference<ClassLoader> loader = new WeakReference<>(host.classLoader("lettered"));
		host.disable("lettered");

		assertTrue(LeakCheck.collected(loader, Duration.ofSeconds(10)), "the library manager kept the extension's loader");
	}

	@Test
	void anExtensionWhoseLibrariesCannotBeLoadedFailsToStartAndSaysWhich() throws Exception {
		lettered(folder.resolve("source/missing.jar").toUri().toString());

		start();

		InstalledExtension lettered = host.find("lettered").orElseThrow();
		assertEquals(ExtensionState.FAILED, lettered.getState());
		assertTrue(lettered.getFailure().getMessage().contains("org.example:letters:1.0.0"),
				lettered.getFailure().getMessage());
	}

	private void lettered(String libraryUrl) throws Exception {
		ExtensionJar.of(DESCRIPTOR, """
				id: lettered
				version: 1.0.0
				main: org.example.attache.LetteredModule
				""")
				.with(LetteredModule.class)
				.file("META-INF/attache/lettered/attache.xml", """
						<?xml version="1.0" encoding="UTF-8"?>
						<attache-descriptor project-path=":lettered" project-name="lettered" add-maven-central="false">
						  <libraries>
						    <library group-id="org.example" artifact-id="letters" version="1.0.0">
						      <urls>
						        <url>%s</url>
						      </urls>
						    </library>
						  </libraries>
						</attache-descriptor>
						""".formatted(libraryUrl))
				.write(folder.resolve("modules/lettered.jar"));
	}

	private void start() throws Exception {
		HostOptions options = HostOptions.builder()
				.kind("module")
				.folder(folder.resolve("modules"))
				.descriptorFile(DESCRIPTOR)
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
				.exportLayer(new ExportLayer(getClass().getClassLoader(), List.of("me.whereareiam.chassis")))
				.preparation(AttacheLibraries.builder()
						.folder(folder.resolve("libraries"))
						.verbosity(VerbosityMode.QUIET)
						.build())
				.build();
		host = new ChassisHost<>(options, AttacheModule.class, base -> base, ExtensionFactory.CONSTRUCTOR);
		host.start();
	}

	private ExtensionState state() {
		return host.find("lettered").orElseThrow().getState();
	}
}
