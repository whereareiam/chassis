package me.whereareiam.chassis.adapter.attache;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.common.ChassisHost;
import me.whereareiam.chassis.common.HostOptions;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.InstalledExtension;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
import me.whereareiam.chassis.testing.ExtensionJar;
import me.whereareiam.chassis.testing.LeakCheck;
import me.whereareiam.chassis.type.ExtensionState;
import me.whereareiam.attache.LibraryTransformer;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.VerbosityMode;
import org.example.attache.GroupedModule;
import org.example.attache.LetteredModule;
import org.example.attache.driver.Driver;
import org.example.attache.library.Letters;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttacheLibrariesTest {
	private static final String DESCRIPTOR = "attache-module.yml";

	@TempDir
	Path folder;

	private ChassisHost<ExtensionContext, Void> host;
	private AttacheLibraries libraries;

	@BeforeEach
	void clearMarks() {
		System.clearProperty("chassis.test.attache");
		System.clearProperty("chassis.test.attache.before");
		System.clearProperty("chassis.test.attache.after");
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
	void theTransformersTheHostNamesRunOnEveryExtensionsLibrariesBeforeTheyLoad() throws Exception {
		Path library = ExtensionJar.of("letters.txt", "").with(Letters.class).write(folder.resolve("source/letters-1.0.0.jar"));
		lettered(library.toUri().toString());
		List<String> seen = new ArrayList<>();
		LibraryTransformer copying = new LibraryTransformer() {
			@Override
			public @NotNull String getId() {
				return "copying";
			}

			@Override
			public @NotNull String getVersion() {
				return "1";
			}

			@Override
			public @NotNull Path transform(@NotNull LibraryRequest request, @NotNull Path input, @NotNull Path output) throws IOException {
				seen.add(request.getArtifactId());
				Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);

				return output;
			}
		};

		start(copying);

		assertEquals(ExtensionState.ENABLED, state());
		assertEquals("HELLO", System.getProperty("chassis.test.attache"));
		assertEquals(List.of("letters"), seen);
		assertTrue(Files.exists(folder.resolve("libraries/org/example/letters/1.0.0/letters-1.0.0-copying-1.jar")));
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

	@Test
	void anExtensionLoadsAGroupOfItsLibrariesWhenItAsks() throws Exception {
		Path driver = ExtensionJar.of("driver.txt", "").with(Driver.class).write(folder.resolve("source/driver-1.0.0.jar"));
		ExtensionJar.of(DESCRIPTOR, """
				id: grouped
				version: 1.0.0
				main: org.example.attache.GroupedModule
				""")
				.with(GroupedModule.class)
				.file("META-INF/attache/grouped/attache.xml", """
						<?xml version="1.0" encoding="UTF-8"?>
						<attache-descriptor project-path=":grouped" project-name="grouped" add-maven-central="false">
						  <libraries>
						    <library group-id="org.example" artifact-id="driver" version="1.0.0" group="sqlite">
						      <urls>
						        <url>%s</url>
						      </urls>
						    </library>
						  </libraries>
						</attache-descriptor>
						""".formatted(driver.toUri()))
				.write(folder.resolve("modules/grouped.jar"));

		start();

		assertEquals(ExtensionState.ENABLED, host.find("grouped").orElseThrow().getState());
		assertEquals("false", System.getProperty("chassis.test.attache.before"));
		assertEquals("true", System.getProperty("chassis.test.attache.after"));
		assertThrows(IllegalArgumentException.class, () -> libraries.groups("grouped").load("postgres"));

		WeakReference<ClassLoader> loader = new WeakReference<>(host.classLoader("grouped"));
		host.disable("grouped");

		assertTrue(LeakCheck.collected(loader, Duration.ofSeconds(10)), "the groups kept the extension's loader");
		assertThrows(IllegalStateException.class, () -> libraries.groups("grouped").load("sqlite"));
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

	private void start(LibraryTransformer... transformers) throws Exception {
		libraries = AttacheLibraries.builder()
				.folder(folder.resolve("libraries"))
				.verbosity(VerbosityMode.QUIET)
				.transformers(List.of(transformers))
				.build();
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
					public @NotNull Optional<PlatformPeer> peer(@NotNull String id) {
						return Optional.empty();
					}
				})
				.exportLayer(new ExportLayer(getClass().getClassLoader(), List.of("me.whereareiam.chassis")))
				.preparation(libraries)
				.build();
		host = new ChassisHost<>(options, AttacheModule.class,
				base -> new GroupedContext(base, libraries.groups(base.descriptor().getId())), ExtensionFactory.CONSTRUCTOR);
		host.start();
	}

	private ExtensionState state() {
		return host.find("lettered").orElseThrow().getState();
	}
}
