package me.whereareiam.chassis.adapter.attache;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.platform.standalone.StandaloneLibraryManager;
import me.whereareiam.attache.type.Level;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.ExtensionFactory;
import me.whereareiam.chassis.common.ChassisHost;
import me.whereareiam.chassis.common.HostOptions;
import me.whereareiam.chassis.common.loading.ExportLayer;
import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.testing.ExtensionJar;
import me.whereareiam.chassis.testing.LeakCheck;
import me.whereareiam.chassis.type.ExtensionState;
import org.example.library.LibraryModule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttacheLibraryLoaderTest {
	private static final String DESCRIPTOR = "test-module.yml";

	@TempDir
	Path folder;

	private URLClassLoader hostLoader;
	private StandaloneLibraryManager hostLibraries;
	private ChassisHost<ExtensionContext> host;

	@BeforeEach
	void clearMarks() {
		System.clearProperty("chassis.test.library.own");
		System.clearProperty("chassis.test.library.driver");
		System.clearProperty("chassis.test.library.group");
	}

	@AfterEach
	void stop() throws IOException {
		if (host != null) host.stop();
		if (hostLibraries != null) hostLibraries.close();
		if (hostLoader != null) hostLoader.close();
	}

	@Test
	void anExtensionHasItsLibrariesBeforeItStartsAndItsGroupsWhenItAsks() throws Exception {
		Path greeting = libraryJar("greeting", "dev.example.greeting.Greeting");
		Path driver = libraryJar("driver", "dev.example.driver.Driver");
		extension(library("greeting", greeting, null) + library("driver", driver, "sqlite"));

		start();

		assertEquals(ExtensionState.ENABLED, host.find("library").orElseThrow().getState());
		assertEquals("true", System.getProperty("chassis.test.library.own"));
		assertEquals("false", System.getProperty("chassis.test.library.driver"));
		assertEquals("true", System.getProperty("chassis.test.library.group"));

		WeakReference<ClassLoader> loader = new WeakReference<>(host.classLoader("library"));
		host.disable("library");
		assertTrue(LeakCheck.collected(loader, Duration.ofSeconds(10)), "the host kept the extension or its libraries");
	}

	@Test
	void anExtensionWhoseLibraryCannotBeLoadedFails() throws Exception {
		extension(library("missing", folder.resolve("missing.jar"), null));

		start();

		assertEquals(ExtensionState.FAILED, host.find("library").orElseThrow().getState());
		assertTrue(host.find("library").orElseThrow().getFailure().getMessage().startsWith("'library' could not load its libraries: "),
				host.find("library").orElseThrow().getFailure().getMessage());
	}

	private void start() throws Exception {
		// A host's plugin has a class loader of its own, as on Bukkit and Velocity; its manager adds the host's libraries there.
		hostLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());
		hostLibraries = new StandaloneLibraryManager(new SilentLogging(), folder.resolve("data"), ".libraries", hostLoader);
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
				.libraries(new AttacheLibraryLoader(hostLibraries))
				.build();
		host = new ChassisHost<>(options, AttacheModule.class, base -> base, ExtensionFactory.CONSTRUCTOR);
		host.start();
	}

	private void extension(String libraries) throws IOException {
		ExtensionJar.of(DESCRIPTOR, """
						id: library
						version: 1.0.0
						main: org.example.library.LibraryModule
						""")
				.with(LibraryModule.class)
				.file("META-INF/attache/library/attache.xml", """
						<?xml version="1.0" encoding="UTF-8"?>
						<attache-descriptor project-path=":library" project-name="library" add-maven-central="false">
						  <libraries>
						%s
						  </libraries>
						</attache-descriptor>
						""".formatted(libraries))
				.write(folder.resolve("modules/library.jar"));
	}

	private static String library(String artifact, Path jar, String group) {
		return """
				    <library group-id="dev.example" artifact-id="%s" version="1.0.0"%s>
				      <urls><url>%s</url></urls>
				    </library>
				""".formatted(artifact, group == null ? "" : " group=\"" + group + "\"", jar.toUri());
	}

	private Path libraryJar(String name, String className) throws IOException {
		int split = className.lastIndexOf('.');
		Path source = folder.resolve(name + "-source").resolve(className.replace('.', '/') + ".java");
		Path classes = folder.resolve(name + "-classes");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "package " + className.substring(0, split) + "; public final class "
				+ className.substring(split + 1) + " {}");

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "Tests need a JDK with its Java compiler");
		try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
			assertTrue(compiler.getTask(null, files, null, List.of("--release", "21", "-d", classes.toString()), null,
					files.getJavaFileObjects(source)).call());
		}

		Path jar = folder.resolve(name + ".jar");
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar)); var paths = Files.walk(classes)) {
			for (Path file : paths.filter(Files::isRegularFile).toList()) {
				output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
				Files.copy(file, output);
				output.closeEntry();
			}
		}

		return jar;
	}

	private static final class SilentLogging implements LoggingHelper {
		@Override
		public void log(@NotNull Level level, @NotNull String message) {
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
		}
	}
}
